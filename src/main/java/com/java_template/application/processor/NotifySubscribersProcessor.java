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

        List<Subscriber> activeSubscribers = new ArrayList<>();
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
                        Subscriber s = objectMapper.treeToValue(payload.getData(), Subscriber.class);
                        if (s != null) {
                            activeSubscribers.add(s);
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to deserialize subscriber payload: {}", ex.getMessage(), ex);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to fetch active subscribers: {}", ex.getMessage(), ex);
        }

        int notifiedCount = 0;
        HttpClient httpClient = HttpClient.newBuilder().build();
        for (Subscriber s : activeSubscribers) {
            if (s == null) continue;
            String pref = s.getDeliveryPreference();
            if (pref != null && "webhook".equalsIgnoreCase(pref)) {
                String webhook = s.getWebhookUrl();
                if (webhook == null || webhook.isBlank()) {
                    logger.warn("Subscriber {} has webhook preference but no webhookUrl defined", s.getSubscriberId());
                    continue;
                }
                try {
                    Map<String, Object> payloadMap = new HashMap<>();
                    payloadMap.put("jobId", job.getJobId());
                    payloadMap.put("status", job.getStatus());
                    payloadMap.put("summary", job.getSummary());
                    payloadMap.put("finishedAt", job.getFinishedAt());
                    // include simple metadata: number of active subscribers at the time
                    payloadMap.put("activeSubscribersCount", activeSubscribers.size());

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
                        logger.info("Successfully notified subscriber {} via webhook (status={})", s.getSubscriberId(), statusCode);
                    } else {
                        logger.warn("Failed to notify subscriber {} via webhook, statusCode={}", s.getSubscriberId(), statusCode);
                    }
                } catch (Exception ex) {
                    logger.error("Error notifying subscriber {} via webhook: {}", s.getSubscriberId(), ex.getMessage(), ex);
                }
            } else if (pref != null && "email".equalsIgnoreCase(pref)) {
                // Email delivery not implemented in this processor; log intent.
                logger.info("Email notification requested for subscriber {}. Email delivery not implemented; skipping.", s.getSubscriberId());
                // We consider email notifications attempted but not counted as delivered.
            } else {
                logger.warn("Unknown delivery preference '{}' for subscriber {}. Skipping.", pref, s.getSubscriberId());
            }
        }

        // Update job state - do not call entityService to update this job (workflow will persist returned entity)
        job.setStatus("NOTIFIED_SUBSCRIBERS");
        String now = Instant.now().toString();
        job.setFinishedAt(now);
        String prevSummary = job.getSummary() != null ? job.getSummary() : "";
        String postSummary = prevSummary.isBlank() ? String.format("notified %d subscribers", notifiedCount)
            : String.format("%s; notified %d subscribers", prevSummary, notifiedCount);
        job.setSummary(postSummary);

        logger.info("Job {} transitioned to NOTIFIED_SUBSCRIBERS (notifiedCount={})", job.getJobId(), notifiedCount);

        return job;
    }
}