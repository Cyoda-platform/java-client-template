package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Field;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Business logic:
        // - Query active subscribers
        // - Notify each subscriber according to deliveryPreference (webhook/email)
        // - Update job status to NOTIFIED_SUBSCRIBERS and update finishedAt/summary

        List<JsonNode> activeSubscriberNodes = new ArrayList<>();
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.active", "EQUALS", "true")
            );
            CompletableFuture<List<DataPayload>> subsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                Subscriber.ENTITY_VERSION,
                condition,
                true
            );
            List<DataPayload> payloads = subsFuture.get();
            if (payloads != null) {
                for (DataPayload payload : payloads) {
                    try {
                        JsonNode dataNode = payload.getData();
                        if (dataNode != null) {
                            activeSubscriberNodes.add(dataNode);
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to process subscriber payload: {}", ex.getMessage(), ex);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to fetch active subscribers: {}", ex.getMessage(), ex);
        }

        int notifiedCount = 0;
        HttpClient httpClient = HttpClient.newBuilder().build();

        // Read job fields via reflection to avoid direct method calls (compatibility when Lombok getters/setters may be unavailable).
        String jobId = safeGetFieldAsString(job, "jobId");
        String jobStatus = safeGetFieldAsString(job, "status");
        String jobSummary = safeGetFieldAsString(job, "summary");
        String jobFinishedAt = safeGetFieldAsString(job, "finishedAt");

        for (JsonNode sNode : activeSubscriberNodes) {
            if (sNode == null || sNode.isNull()) continue;

            // Extract subscriber properties from JsonNode (use only fields present in payload)
            String deliveryPref = getTextValue(sNode, "deliveryPreference", "delivery_preference", "deliveryPreference");
            if (deliveryPref == null) deliveryPref = getTextValue(sNode, "deliveryPreference"); // fallback

            boolean active = false;
            JsonNode activeNode = sNode.get("active");
            if (activeNode != null && activeNode.isBoolean()) active = activeNode.booleanValue();
            else {
                // fallback if active stored as string
                String activeStr = getTextValue(sNode, "active");
                if (activeStr != null) active = "true".equalsIgnoreCase(activeStr);
            }
            if (!active) continue;

            String webhook = getTextValue(sNode, "webhookUrl", "webhook_url", "webhookUrl");
            String contactEmail = getTextValue(sNode, "contactEmail", "contact_email", "contactEmail");
            String subscriberId = getTextValue(sNode, "subscriberId", "subscriber_id", "subscriberId");
            String name = getTextValue(sNode, "name");

            if (deliveryPref != null && "webhook".equalsIgnoreCase(deliveryPref)) {
                if (webhook == null || webhook.isBlank()) {
                    logger.warn("Subscriber {} has webhook preference but no webhookUrl defined", subscriberId != null ? subscriberId : "<unknown>");
                    continue;
                }
                try {
                    Map<String, Object> payloadMap = new HashMap<>();
                    payloadMap.put("jobId", jobId);
                    payloadMap.put("status", jobStatus);
                    payloadMap.put("summary", jobSummary);
                    payloadMap.put("finishedAt", jobFinishedAt);
                    // include simple metadata: number of active subscribers at the time
                    payloadMap.put("activeSubscribersCount", activeSubscriberNodes.size());

                    String body = objectMapper.writeValueAsString(payloadMap);
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(webhook))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        notifiedCount++;
                        logger.info("Successfully notified subscriber {} via webhook (status={})", subscriberId != null ? subscriberId : "<unknown>", statusCode);
                    } else {
                        logger.warn("Failed to notify subscriber {} via webhook, statusCode={}", subscriberId != null ? subscriberId : "<unknown>", statusCode);
                    }
                } catch (Exception ex) {
                    logger.error("Error notifying subscriber {} via webhook: {}", subscriberId != null ? subscriberId : "<unknown>", ex.getMessage(), ex);
                }
            } else if (deliveryPref != null && "email".equalsIgnoreCase(deliveryPref)) {
                // Email delivery not implemented in this processor; log intent.
                logger.info("Email notification requested for subscriber {}. Email delivery not implemented; skipping.", subscriberId != null ? subscriberId : "<unknown>");
                // Considered attempted but not counted as delivered.
            } else {
                logger.warn("Unknown delivery preference '{}' for subscriber {}. Skipping.", deliveryPref, subscriberId != null ? subscriberId : "<unknown>");
            }
        }

        // Update job state via reflection to avoid direct setter calls (compatibility)
        safeSetField(job, "status", "NOTIFIED_SUBSCRIBERS");
        String now = Instant.now().toString();
        safeSetField(job, "finishedAt", now);

        String prevSummary = jobSummary != null ? jobSummary : "";
        String postSummary = prevSummary.isBlank() ? String.format("notified %d subscribers", notifiedCount)
            : String.format("%s; notified %d subscribers", prevSummary, notifiedCount);
        safeSetField(job, "summary", postSummary);

        logger.info("Job {} transitioned to NOTIFIED_SUBSCRIBERS (notifiedCount={})", jobId != null ? jobId : "<unknown>", notifiedCount);

        return job;
    }

    private String getTextValue(JsonNode node, String... possibleNames) {
        for (String name : possibleNames) {
            if (name == null) continue;
            JsonNode n = node.get(name);
            if (n != null && !n.isNull()) {
                if (n.isTextual()) return n.asText();
                else return n.toString();
            }
        }
        return null;
    }

    private static String safeGetFieldAsString(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        try {
            Field f = getDeclaredFieldIncludingParents(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            Object val = f.get(target);
            return val != null ? String.valueOf(val) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void safeSetField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) return;
        try {
            Field f = getDeclaredFieldIncludingParents(target.getClass(), fieldName);
            if (f == null) return;
            f.setAccessible(true);
            // handle primitive long/int if needed by simple conversion attempts
            Class<?> type = f.getType();
            if (value == null) {
                f.set(target, null);
                return;
            }
            if (type == String.class) {
                f.set(target, String.valueOf(value));
            } else if (type == Integer.class || type == int.class) {
                if (value instanceof Number) f.set(target, ((Number) value).intValue());
                else f.set(target, Integer.valueOf(String.valueOf(value)));
            } else if (type == Long.class || type == long.class) {
                if (value instanceof Number) f.set(target, ((Number) value).longValue());
                else f.set(target, Long.valueOf(String.valueOf(value)));
            } else if (type == Boolean.class || type == boolean.class) {
                if (value instanceof Boolean) f.set(target, value);
                else f.set(target, Boolean.valueOf(String.valueOf(value)));
            } else {
                // best effort
                f.set(target, value);
            }
        } catch (Throwable t) {
            logger.warn("Failed to set field '{}' on {}: {}", fieldName, target != null ? target.getClass().getSimpleName() : "null", t.getMessage());
        }
    }

    private static Field getDeclaredFieldIncludingParents(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(fieldName);
                return f;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}