package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotifySubscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        try {
            logger.info("Notifying subscribers for job: {} with status {}", job.getTechnicalId(), job.getStatus());

            // Fetch subscribers (active=true)
            ArrayNode subs = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.active", "EQUALS", "true")
                ),
                true
            ).join();

            if (subs == null || subs.size() == 0) {
                logger.info("No active subscribers found for job {}", job.getTechnicalId());
                job.setStatus("NOTIFIED_SUBSCRIBERS");
                persistJobSafe(job);
                return job;
            }

            // Fetch laureates for this job to support filters and payloads
            ArrayNode laureates = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.sourceJobTechnicalId", "EQUALS", job.getTechnicalId())
                ),
                true
            ).join();

            for (JsonNode sNode : subs) {
                try {
                    Subscriber subscriber = objectMapper.treeToValue(sNode, Subscriber.class);
                    if (subscriber == null) continue;
                    if (subscriber.getActive() == null || !subscriber.getActive()) continue;

                    // Parse preferences
                    JsonNode prefs = null;
                    try {
                        if (subscriber.getPreferences() != null && !subscriber.getPreferences().isBlank()) {
                            prefs = objectMapper.readTree(subscriber.getPreferences());
                        }
                    } catch (Exception pe) {
                        logger.warn("Unable to parse preferences for subscriber {}: {}", subscriber.getTechnicalId(), pe.getMessage());
                    }

                    boolean notifyOnSuccess = prefs != null && prefs.has("notifyOnSuccess") ? prefs.get("notifyOnSuccess").asBoolean(true) : true;
                    boolean notifyOnFailure = prefs != null && prefs.has("notifyOnFailure") ? prefs.get("notifyOnFailure").asBoolean(true) : true;
                    String includePayload = prefs != null && prefs.has("includePayload") ? prefs.get("includePayload").asText("summary") : "summary";
                    JsonNode filters = prefs != null && prefs.has("filters") ? prefs.get("filters") : null;

                    // Decide if subscriber wants notifications for this job status
                    if ("SUCCEEDED".equalsIgnoreCase(job.getStatus()) && !notifyOnSuccess) continue;
                    if (("FAILED".equalsIgnoreCase(job.getStatus()) || "PARTIAL_FAILURE".equalsIgnoreCase(job.getStatus())) && !notifyOnFailure) continue;

                    // Apply filters: if filters present, reduce laureates to matching ones
                    ArrayNode matchedLaureates = objectMapper.createArrayNode();
                    if (laureates != null) {
                        for (JsonNode lNode : laureates) {
                            boolean matches = true;
                            if (filters != null) {
                                if (filters.has("category") && filters.get("category").isArray()) {
                                    boolean any = false;
                                    for (JsonNode cat : filters.get("category")) {
                                        if (lNode.has("category") && cat.asText().equalsIgnoreCase(lNode.get("category").asText(""))) { any = true; break; }
                                    }
                                    if (!any) matches = false;
                                }
                                if (filters.has("year") && filters.get("year").isArray()) {
                                    boolean any = false;
                                    for (JsonNode y : filters.get("year")) {
                                        if (lNode.has("year") && y.asText().equalsIgnoreCase(lNode.get("year").asText(""))) { any = true; break; }
                                    }
                                    if (!any) matches = false;
                                }
                            }
                            if (matches) matchedLaureates.add(lNode);
                        }
                    }

                    if (filters != null && matchedLaureates.size() == 0) {
                        // Subscriber filters exclude all laureates of this job; skip notification
                        logger.info("Subscriber {} preferences exclude all laureates for job {}; skipping", subscriber.getTechnicalId(), job.getTechnicalId());
                        continue;
                    }

                    // Build payload
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("jobTechnicalId", job.getTechnicalId());
                    payload.put("status", job.getStatus());
                    payload.put("fetchedRecordCount", job.getFetchedRecordCount() == null ? 0 : job.getFetchedRecordCount());
                    payload.put("persistedRecordCount", job.getPersistedRecordCount() == null ? 0 : job.getPersistedRecordCount());
                    payload.put("succeededCount", job.getSucceededCount() == null ? 0 : job.getSucceededCount());
                    payload.put("failedCount", job.getFailedCount() == null ? 0 : job.getFailedCount());

                    if ("none".equalsIgnoreCase(includePayload)) {
                        // nothing more
                    } else if ("summary".equalsIgnoreCase(includePayload)) {
                        ArrayNode ids = payload.putArray("laureateIds");
                        ArrayNode source = (matchedLaureates.size() > 0) ? matchedLaureates : laureates;
                        if (source != null) {
                            for (JsonNode ln : source) {
                                if (ln.has("id")) ids.add(ln.get("id").asInt());
                            }
                        }
                    } else if ("full".equalsIgnoreCase(includePayload)) {
                        ArrayNode arr = payload.putArray("laureates");
                        ArrayNode source = (matchedLaureates.size() > 0) ? matchedLaureates : laureates;
                        if (source != null) {
                            for (JsonNode ln : source) arr.add(ln);
                        }
                    } else {
                        // default to summary
                        ArrayNode ids = payload.putArray("laureateIds");
                        ArrayNode source = (matchedLaureates.size() > 0) ? matchedLaureates : laureates;
                        if (source != null) {
                            for (JsonNode ln : source) {
                                if (ln.has("id")) ids.add(ln.get("id").asInt());
                            }
                        }
                    }

                    // Deliver based on contactType
                    boolean delivered = false;
                    String failureReason = null;
                    if (subscriber.getContactType() != null && subscriber.getContactType().equalsIgnoreCase("webhook")) {
                        String url = subscriber.getContactDetails();
                        String idempotencyKey = job.getTechnicalId() + ":" + subscriber.getTechnicalId();
                        try {
                            delivered = deliverWebhook(url, payload, idempotencyKey);
                        } catch (Exception de) {
                            delivered = false;
                            failureReason = de.getMessage();
                        }
                    } else if (subscriber.getContactType() != null && subscriber.getContactType().equalsIgnoreCase("email")) {
                        // Email delivery is simulated: in real scenario delegate to email service
                        logger.info("Simulating email delivery to {} for job {}", subscriber.getContactDetails(), job.getTechnicalId());
                        delivered = true;
                    } else {
                        logger.warn("Unknown contactType {} for subscriber {}", subscriber.getContactType(), subscriber.getTechnicalId());
                        delivered = false;
                        failureReason = "Unknown contactType";
                    }

                    // Update subscriber last notified timestamp only on success
                    if (delivered) {
                        subscriber.setLastNotifiedAt(Instant.now().toString());
                    }

                    // Persist subscriber changes
                    try {
                        if (subscriber.getTechnicalId() != null && !subscriber.getTechnicalId().isEmpty()) {
                            entityService.updateItem(
                                Subscriber.ENTITY_NAME,
                                String.valueOf(Subscriber.ENTITY_VERSION),
                                java.util.UUID.fromString(subscriber.getTechnicalId()),
                                subscriber
                            ).join();
                        }
                    } catch (Exception pe) {
                        logger.warn("Unable to persist subscriber {} notification status: {}", subscriber.getTechnicalId(), pe.getMessage());
                    }

                    if (!delivered) {
                        logger.warn("Delivery to subscriber {} failed: {}", subscriber.getTechnicalId(), failureReason);
                    } else {
                        logger.info("Notification delivered to subscriber {}", subscriber.getTechnicalId());
                    }

                } catch (Exception se) {
                    logger.error("Error while processing subscriber for job {}: {}", job.getTechnicalId(), se.getMessage(), se);
                }
            }

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            persistJobSafe(job);

        } catch (Exception e) {
            logger.error("Error while notifying subscribers for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
        }

        return job;
    }

    private boolean deliverWebhook(String url, JsonNode payload, String idempotencyKey) throws Exception {
        int attempts = 0;
        int maxAttempts = 3;
        Duration backoff = Duration.ofSeconds(2);
        while (attempts < maxAttempts) {
            attempts++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code >= 200 && code < 300) return true;
                if (code >= 500 && attempts < maxAttempts) {
                    Thread.sleep(backoff.toMillis());
                    backoff = backoff.multipliedBy(2);
                    continue;
                }
                // Non-retryable
                throw new RuntimeException("HTTP error " + code + ": " + response.body());
            } catch (Exception e) {
                if (attempts >= maxAttempts) throw e;
                Thread.sleep(backoff.toMillis());
                backoff = backoff.multipliedBy(2);
            }
        }
        return false;
    }

    private void persistJobSafe(Job job) {
        try {
            if (job.getTechnicalId() != null && !job.getTechnicalId().isEmpty()) {
                try {
                    entityService.updateItem(
                        Job.ENTITY_NAME,
                        String.valueOf(Job.ENTITY_VERSION),
                        java.util.UUID.fromString(job.getTechnicalId()),
                        job
                    ).join();
                } catch (Exception e) {
                    try {
                        entityService.addItem(
                            Job.ENTITY_NAME,
                            String.valueOf(Job.ENTITY_VERSION),
                            job
                        ).join();
                    } catch (Exception ex) {
                        logger.warn("Unable to persist job {}: {}", job.getTechnicalId(), ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Persist job failed for {}: {}", job.getTechnicalId(), e.getMessage());
        }
    }
}
