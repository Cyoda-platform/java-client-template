package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        if (job == null) return job;

        String jobState = job.getState();
        // Only send notifications when job reached SUCCEEDED or FAILED
        if (jobState == null) {
            logger.warn("Job state is null for job id {}", job.getId());
            return job;
        }

        if (!"SUCCEEDED".equalsIgnoreCase(jobState) && !"FAILED".equalsIgnoreCase(jobState)) {
            logger.info("Job {} is in state {} - no notification required", job.getId(), jobState);
            return job;
        }

        Instant now = Instant.now();
        String nowIso = now.toString();

        List<String> errors = new ArrayList<>();

        // Build summary payload
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("jobId", job.getId());
        summary.put("state", job.getState());
        if (job.getStartedAt() != null) summary.put("startedAt", job.getStartedAt());
        if (job.getFinishedAt() != null) summary.put("finishedAt", job.getFinishedAt());
        if (job.getAttempts() != null) summary.put("attempts", job.getAttempts());
        if (job.getLastError() != null) summary.put("lastError", job.getLastError());
        summary.put("notifiedAt", nowIso);

        // Find active subscribers
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.active", "EQUALS", "true")
        );

        ArrayNode subscribersNodes;
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );
            subscribersNodes = itemsFuture.join();
        } catch (Exception e) {
            logger.error("Failed to retrieve subscribers for notification for job {}: {}", job.getId(), e.getMessage(), e);
            job.setLastError("Failed to retrieve subscribers: " + e.getMessage());
            // mark as notified even if subscribers couldn't be retrieved to avoid retry loops
            job.setState("NOTIFIED_SUBSCRIBERS");
            job.setFinishedAt(nowIso);
            return job;
        }

        if (subscribersNodes == null || subscribersNodes.isEmpty()) {
            logger.info("No active subscribers found for job {}", job.getId());
        } else {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            for (JsonNode node : subscribersNodes) {
                try {
                    ObjectNode subscriberNode = (ObjectNode) node;
                    // extract technicalId if present to support updates
                    String technicalId = null;
                    if (subscriberNode.hasNonNull("technicalId")) {
                        technicalId = subscriberNode.get("technicalId").asText();
                    }

                    Subscriber subscriber = objectMapper.treeToValue(subscriberNode, Subscriber.class);

                    if (subscriber == null || subscriber.getActive() == null || !subscriber.getActive()) {
                        continue;
                    }

                    String contact = subscriber.getContact();
                    String type = subscriber.getType();

                    // payload per subscriber could be extended with filters - currently send summary
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.set("summary", summary);

                    if ("webhook".equalsIgnoreCase(type) && contact != null && !contact.isBlank()) {
                        try {
                            String payloadStr = objectMapper.writeValueAsString(payload);
                            HttpRequest httpRequest = HttpRequest.newBuilder()
                                .uri(URI.create(contact))
                                .timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payloadStr))
                                .build();

                            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                            int status = response.statusCode();
                            if (status < 200 || status >= 300) {
                                String err = String.format("Webhook notify failed for subscriber %s with status %d", subscriber.getId(), status);
                                logger.warn(err);
                                errors.add(err);
                            } else {
                                logger.info("Successfully notified webhook subscriber {} (status {})", subscriber.getId(), status);
                            }
                        } catch (Exception e) {
                            String err = String.format("Exception notifying webhook subscriber %s: %s", subscriber.getId(), e.getMessage());
                            logger.warn(err, e);
                            errors.add(err);
                        }
                    } else if ("email".equalsIgnoreCase(type) && contact != null && !contact.isBlank()) {
                        // Simulate email sending by logging; in real implementation integrate with mail service
                        try {
                            StringBuilder emailBuilder = new StringBuilder();
                            emailBuilder.append("To: ").append(contact).append("\n");
                            emailBuilder.append("Subject: Job ").append(job.getId()).append(" - ").append(job.getState()).append("\n\n");
                            emailBuilder.append("Summary:\n");
                            emailBuilder.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
                            logger.info("Simulated email to subscriber {}: \n{}", subscriber.getId(), emailBuilder.toString());
                        } catch (Exception e) {
                            String err = String.format("Exception preparing email for subscriber %s: %s", subscriber.getId(), e.getMessage());
                            logger.warn(err, e);
                            errors.add(err);
                        }
                    } else {
                        String warn = String.format("Unsupported subscriber type '%s' for subscriber %s", type, subscriber.getId());
                        logger.warn(warn);
                        errors.add(warn);
                    }

                    // Update subscriber.lastNotifiedAt
                    try {
                        subscriber.setLastNotifiedAt(nowIso);
                        if (technicalId != null && !technicalId.isBlank()) {
                            CompletableFuture<UUID> updated = entityService.updateItem(
                                Subscriber.ENTITY_NAME,
                                String.valueOf(Subscriber.ENTITY_VERSION),
                                UUID.fromString(technicalId),
                                subscriber
                            );
                            updated.join();
                        } else {
                            // If no technicalId is present, attempt to add as fallback (shouldn't normally happen)
                            CompletableFuture<UUID> added = entityService.addItem(
                                Subscriber.ENTITY_NAME,
                                String.valueOf(Subscriber.ENTITY_VERSION),
                                subscriber
                            );
                            added.join();
                        }
                    } catch (Exception e) {
                        String err = String.format("Failed to update subscriber %s lastNotifiedAt: %s", subscriber.getId(), e.getMessage());
                        logger.warn(err, e);
                        errors.add(err);
                    }

                } catch (Exception e) {
                    String err = "Failed to process a subscriber node: " + e.getMessage();
                    logger.warn(err, e);
                    errors.add(err);
                }
            }
        }

        // Finalize job state
        job.setState("NOTIFIED_SUBSCRIBERS");
        job.setFinishedAt(nowIso);
        if (!errors.isEmpty()) {
            job.setLastError(String.join("; ", errors));
        } else {
            job.setLastError(null);
        }

        logger.info("Notification processing completed for job {} with {} errors", job.getId(), errors.size());
        return job;
    }
}