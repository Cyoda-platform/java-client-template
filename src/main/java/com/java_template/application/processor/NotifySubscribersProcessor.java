package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory,
                                      EntityService entityService,
                                      ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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

        // Prepare: mark start of notification attempt (Cyoda will persist job changes)
        logger.info("NotifySubscribersProcessor: starting notifications for job id={}, status={}", job.getId(), job.getStatus());

        if (job.getSubscribersSnapshot() == null || job.getSubscribersSnapshot().isEmpty()) {
            logger.info("No subscribers to notify for job id={}", job.getId());
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            job.setNotificationsSent(true);
            return job;
        }

        for (String subscriberBusinessId : job.getSubscribersSnapshot()) {
            if (subscriberBusinessId == null || subscriberBusinessId.isBlank()) {
                logger.warn("Skipping blank subscriber id in job {}", job.getId());
                continue;
            }

            try {
                // Build condition to find subscriber by business id
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.subscriberId", "EQUALS", subscriberBusinessId)
                );

                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        Subscriber.ENTITY_NAME,
                        String.valueOf(Subscriber.ENTITY_VERSION),
                        condition,
                        true
                );

                ArrayNode results = itemsFuture.join();
                if (results == null || results.size() == 0) {
                    String err = "Subscriber not found: " + subscriberBusinessId;
                    logger.warn(err);
                    job.getErrorDetails().add(err);
                    continue;
                }

                // There might be multiple matches; iterate each
                Iterator<JsonNode> it = results.elements();
                while (it.hasNext()) {
                    JsonNode node = it.next();

                    // Extract technicalId if present to allow updating subscriber entity
                    JsonNode techNode = node.get("technicalId");
                    if (techNode == null || techNode.isNull() || techNode.asText().isBlank()) {
                        String err = "Subscriber record missing technicalId for subscriberId=" + subscriberBusinessId;
                        logger.warn(err);
                        job.getErrorDetails().add(err);
                        continue;
                    }
                    UUID technicalId;
                    try {
                        technicalId = UUID.fromString(techNode.asText());
                    } catch (IllegalArgumentException e) {
                        String err = "Invalid technicalId for subscriberId=" + subscriberBusinessId + " value=" + techNode.asText();
                        logger.warn(err);
                        job.getErrorDetails().add(err);
                        continue;
                    }

                    // Convert node to Subscriber object
                    Subscriber subscriber = objectMapper.convertValue(node, Subscriber.class);

                    if (subscriber == null || !subscriber.isValid()) {
                        String err = "Invalid subscriber data for subscriberId=" + subscriberBusinessId;
                        logger.warn(err);
                        job.getErrorDetails().add(err);
                        continue;
                    }

                    // Only notify active subscribers
                    if (subscriber.getActive() == null || !subscriber.getActive()) {
                        logger.info("Subscriber {} is inactive; skipping", subscriberBusinessId);
                        continue;
                    }

                    // Build payload according to preferredPayload
                    String preferred = subscriber.getPreferredPayload() != null ? subscriber.getPreferredPayload().toLowerCase() : "summary";
                    JsonNode payloadNode;
                    switch (preferred) {
                        case "full":
                            payloadNode = objectMapper.valueToTree(job);
                            break;
                        case "minimal":
                            payloadNode = objectMapper.createObjectNode()
                                    .put("id", job.getId())
                                    .put("status", job.getStatus());
                            break;
                        case "summary":
                        default:
                            // summary: id, status, resultSummary (if present)
                            JsonNode summary = objectMapper.createObjectNode();
                            ((com.fasterxml.jackson.databind.node.ObjectNode) summary).put("id", job.getId());
                            ((com.fasterxml.jackson.databind.node.ObjectNode) summary).put("status", job.getStatus());
                            if (job.getResultSummary() != null) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) summary).set("resultSummary", objectMapper.valueToTree(job.getResultSummary()));
                            }
                            payloadNode = summary;
                            break;
                    }

                    boolean sent = false;
                    String notifStatus;

                    // Prefer webhook delivery if present
                    if (subscriber.getContactMethods() != null
                            && subscriber.getContactMethods().getWebhookUrl() != null
                            && !subscriber.getContactMethods().getWebhookUrl().isBlank()) {

                        String webhook = subscriber.getContactMethods().getWebhookUrl();
                        try {
                            String body = objectMapper.writeValueAsString(payloadNode);
                            HttpRequest httpRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(webhook))
                                    .timeout(Duration.ofSeconds(10))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .build();

                            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                            int statusCode = response.statusCode();
                            if (statusCode >= 200 && statusCode < 300) {
                                sent = true;
                                notifStatus = "SENT";
                                logger.info("Notification sent to webhook {} for subscriberId={} jobId={}", webhook, subscriberBusinessId, job.getId());
                            } else {
                                sent = false;
                                notifStatus = "FAILED_HTTP_" + statusCode;
                                String err = "Webhook delivery failed for subscriberId=" + subscriberBusinessId + " status=" + statusCode;
                                logger.warn(err);
                                job.getErrorDetails().add(err);
                            }
                        } catch (Exception e) {
                            sent = false;
                            notifStatus = "FAILED_HTTP_EXCEPTION";
                            String err = "Exception sending webhook for subscriberId=" + subscriberBusinessId + " error=" + e.getMessage();
                            logger.warn(err, e);
                            job.getErrorDetails().add(err);
                        }
                    } else if (subscriber.getContactMethods() != null
                            && subscriber.getContactMethods().getEmail() != null
                            && !subscriber.getContactMethods().getEmail().isBlank()) {
                        // Email delivery not implemented here; mark as QUEUED_EMAIL to indicate intent
                        sent = true;
                        notifStatus = "QUEUED_EMAIL";
                        logger.info("Email queued (simulated) for subscriber {} email={} jobId={}", subscriberBusinessId, subscriber.getContactMethods().getEmail(), job.getId());
                    } else {
                        sent = false;
                        notifStatus = "NO_CONTACT_METHOD";
                        String err = "No contact method available for subscriberId=" + subscriberBusinessId;
                        logger.warn(err);
                        job.getErrorDetails().add(err);
                    }

                    // Update subscriber entity metadata: lastNotifiedJobId and lastNotificationStatus
                    try {
                        subscriber.setLastNotifiedJobId(job.getId());
                        subscriber.setLastNotificationStatus(notifStatus);

                        CompletableFuture<UUID> updateFuture = entityService.updateItem(
                                Subscriber.ENTITY_NAME,
                                String.valueOf(Subscriber.ENTITY_VERSION),
                                technicalId,
                                subscriber
                        );
                        updateFuture.join();
                        logger.info("Subscriber {} updated with notification status {}", subscriberBusinessId, notifStatus);
                    } catch (Exception e) {
                        String err = "Failed to update subscriber record for subscriberId=" + subscriberBusinessId + " error=" + e.getMessage();
                        logger.warn(err, e);
                        job.getErrorDetails().add(err);
                    }
                }

            } catch (Exception e) {
                String err = "Unexpected error while notifying subscriberId=" + subscriberBusinessId + " error=" + e.getMessage();
                logger.warn(err, e);
                job.getErrorDetails().add(err);
            }
        }

        // Finalize job state for notification step
        job.setStatus("NOTIFIED_SUBSCRIBERS");
        job.setNotificationsSent(true);
        job.setFinishedAt(Instant.now().toString());

        logger.info("NotifySubscribersProcessor: completed notifications for job id={}", job.getId());
        return job;
    }
}