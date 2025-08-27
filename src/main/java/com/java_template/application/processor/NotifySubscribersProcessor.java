package com.java_template.application.processor;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

        // Business rules:
        // - Find active subscribers and attempt to notify them about job completion (success or failure).
        // - For webhook subscribers attempt HTTP POST with job summary payload.
        // - For email or other types just log the intended delivery.
        // - Update job state to NOTIFIED_SUBSCRIBERS and set subscribersNotifiedCount to number of attempted deliveries.
        // - If any notification failed, append summary to errorSummary.
        int attemptedNotifications = 0;
        int successfulNotifications = 0;
        List<String> failedNotifications = new ArrayList<>();

        // Build a simple job summary payload
        var jobSummary = new java.util.HashMap<String, Object>();
        jobSummary.put("id", job.getId());
        jobSummary.put("state", job.getState());
        jobSummary.put("startedAt", job.getStartedAt());
        jobSummary.put("finishedAt", job.getFinishedAt());
        jobSummary.put("recordsFetchedCount", job.getRecordsFetchedCount());
        jobSummary.put("recordsProcessedCount", job.getRecordsProcessedCount());
        jobSummary.put("recordsFailedCount", job.getRecordsFailedCount());
        jobSummary.put("errorSummary", job.getErrorSummary());

        // Fetch subscribers
        List<DataPayload> payloads = null;
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                Subscriber.ENTITY_VERSION,
                null, null, null
            );
            payloads = itemsFuture.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching subscribers", ie);
        } catch (ExecutionException ee) {
            logger.error("Error fetching subscribers", ee);
        } catch (Exception ex) {
            logger.error("Unexpected error fetching subscribers", ex);
        }

        if (payloads != null) {
            HttpClient httpClient = HttpClient.newHttpClient();
            for (DataPayload payload : payloads) {
                try {
                    // Convert payload data to Subscriber instance
                    Subscriber subscriber = objectMapper.treeToValue(payload.getData(), Subscriber.class);
                    if (subscriber == null) continue;
                    if (subscriber.getActive() == null || !subscriber.getActive()) continue; // only active subscribers

                    attemptedNotifications++;

                    String contactType = subscriber.getContactType() != null ? subscriber.getContactType() : "";
                    String contactDetails = subscriber.getContactDetails();

                    // Prepare JSON body
                    String body = objectMapper.writeValueAsString(jobSummary);

                    if ("webhook".equalsIgnoreCase(contactType) && contactDetails != null && !contactDetails.isBlank()) {
                        try {
                            HttpRequest httpRequest = HttpRequest.newBuilder()
                                .uri(URI.create(contactDetails))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();

                            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                            int status = response.statusCode();
                            if (status >= 200 && status < 300) {
                                successfulNotifications++;
                            } else {
                                failedNotifications.add(subscriber.getId() + " (webhook returned " + status + ")");
                                logger.warn("Webhook notification to {} returned status {}", contactDetails, status);
                            }
                        } catch (Exception e) {
                            failedNotifications.add(subscriber.getId() + " (webhook error: " + e.getMessage() + ")");
                            logger.error("Failed to send webhook to {}: {}", contactDetails, e.getMessage());
                        }
                    } else if ("email".equalsIgnoreCase(contactType)) {
                        // No mailer available: log the intent
                        successfulNotifications++;
                        logger.info("Pretend-sending email to {}: jobSummary={}", contactDetails, jobSummary);
                    } else {
                        // other contact types: log and count as attempted
                        logger.info("Unknown contactType for subscriber {}: {} - skipping delivery", subscriber.getId(), contactType);
                        failedNotifications.add(subscriber.getId() + " (unsupported contactType)");
                    }
                } catch (Exception e) {
                    logger.error("Failed processing subscriber payload: {}", e.getMessage());
                }
            }
        } else {
            logger.info("No subscribers found.");
        }

        // Update job fields (these will be persisted by Cyoda)
        job.setSubscribersNotifiedCount(attemptedNotifications);
        job.setState("NOTIFIED_SUBSCRIBERS");
        if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
            job.setFinishedAt(Instant.now().toString());
        }

        if (!failedNotifications.isEmpty()) {
            String existing = job.getErrorSummary() != null ? job.getErrorSummary() + " | " : "";
            String failureSummary = "Notification failures: " + String.join(", ", failedNotifications);
            job.setErrorSummary(existing + failureSummary);
            logger.warn("Some notifications failed: {}", failureSummary);
        }

        logger.info("NotifySubscribersProcessor complete for job {}: attempted={}, successful={}, failed={}",
            job.getId(), attemptedNotifications, successfulNotifications, failedNotifications.size());

        return job;
    }
}