package com.java_template.application.processor;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotificationDispatcherProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcherProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotificationDispatcherProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();

        try {
            // Only dispatch if subscriber is active
            if (subscriber.getActive() == null || !subscriber.getActive()) {
                logger.info("Subscriber {} is inactive - skipping notification", subscriber.getSubscriberId());
                return subscriber;
            }

            // Attempt to obtain originating Job technical id from the request (if present)
            String triggeringEntityId = null;
            try {
                triggeringEntityId = context.request().getEntityId();
            } catch (Exception e) {
                logger.debug("Could not read triggering entity id from request: {}", e.getMessage());
            }

            Job job = null;
            if (triggeringEntityId != null) {
                try {
                    CompletableFuture<ObjectNode> jobFuture = entityService.getItem(
                        Job.ENTITY_NAME,
                        String.valueOf(Job.ENTITY_VERSION),
                        UUID.fromString(triggeringEntityId)
                    );
                    ObjectNode jobNode = jobFuture.join();
                    if (jobNode != null) {
                        job = objectMapper.treeToValue(jobNode, Job.class);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to read Job {}: {}", triggeringEntityId, e.getMessage());
                }
            }

            // Simple filter matching:
            // - If subscriber.filters is blank -> send
            // - If filter contains "state=VALUE" -> compare with job.state
            // - If filter contains "resultSummary=VALUE" or "category=VALUE" -> simple contains check in job.resultSummary
            boolean filtersMatch = true;
            String filters = subscriber.getFilters();
            if (filters != null && !filters.isBlank() && job != null) {
                String[] parts = filters.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim().toLowerCase();
                    String value = parts[1].trim();
                    if ("state".equals(key)) {
                        filtersMatch = job.getState() != null && job.getState().equalsIgnoreCase(value);
                    } else if ("resultsummary".equals(key) || "resultSummary".equalsIgnoreCase(key) || "category".equalsIgnoreCase(key)) {
                        String summary = job.getResultSummary() == null ? "" : job.getResultSummary();
                        filtersMatch = summary.toLowerCase().contains(value.toLowerCase());
                    } else {
                        // unknown filter key -> be conservative and send
                        filtersMatch = true;
                    }
                } else {
                    // malformed filter -> send
                    filtersMatch = true;
                }
            }

            if (!filtersMatch) {
                logger.info("Subscriber {} filters do not match job payload - skipping", subscriber.getSubscriberId());
                return subscriber;
            }

            // Build notification payload
            ObjectNode payload = objectMapper.createObjectNode();
            if (job != null) {
                payload.put("jobTechnicalId", triggeringEntityId);
                payload.put("jobId", job.getJobId());
                payload.put("state", job.getState());
                payload.put("resultSummary", job.getResultSummary());
                payload.put("triggeredAt", job.getTriggeredAt());
                payload.put("startedAt", job.getStartedAt());
                payload.put("finishedAt", job.getFinishedAt());
            } else {
                // minimal fallback payload if job not available
                payload.put("message", "Notification from system");
                if (triggeringEntityId != null) payload.put("originTechnicalId", triggeringEntityId);
            }
            payload.put("subscriberId", subscriber.getSubscriberId());
            payload.put("sentAt", Instant.now().toString());

            String contactType = subscriber.getContactType() == null ? "" : subscriber.getContactType().trim().toUpperCase();
            if ("WEBHOOK".equals(contactType)) {
                String webhookUrl = subscriber.getContactAddress();
                if (webhookUrl == null || webhookUrl.isBlank()) {
                    logger.warn("Subscriber {} webhook address is blank", subscriber.getSubscriberId());
                    return subscriber;
                }
                try {
                    HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                    HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                        .build();

                    HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();
                    if (status >= 200 && status < 300) {
                        subscriber.setLastNotifiedAt(Instant.now().toString());
                        logger.info("Successfully delivered webhook notification to subscriber {} (status={})", subscriber.getSubscriberId(), status);
                    } else {
                        logger.warn("Failed to deliver webhook notification to subscriber {} (status={})", subscriber.getSubscriberId(), status);
                        // Do not update lastNotifiedAt to allow retry logic elsewhere
                    }
                } catch (Exception e) {
                    logger.error("Error sending webhook to {}: {}", subscriber.getSubscriberId(), e.getMessage());
                }
            } else if ("EMAIL".equals(contactType)) {
                // Email delivery is not implemented here; mark as notified locally.
                // In a real system, we'd enqueue to an email delivery service.
                subscriber.setLastNotifiedAt(Instant.now().toString());
                logger.info("Simulated email delivery to {} ({})", subscriber.getSubscriberId(), subscriber.getContactAddress());
            } else {
                // Unknown contact type: log and mark lastNotifiedAt so admin can see attempted delivery
                subscriber.setLastNotifiedAt(Instant.now().toString());
                logger.warn("Unknown contactType '{}' for subscriber {}. Marking notification attempt.", subscriber.getContactType(), subscriber.getSubscriberId());
            }

        } catch (Exception ex) {
            logger.error("Unexpected error in NotificationDispatcherProcessor: {}", ex.getMessage(), ex);
        }

        return subscriber;
    }
}