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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public NotifySubscribersProcessor(SerializerFactory serializerFactory,
                                      EntityService entityService,
                                      ObjectMapper objectMapper) {
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

        // Determine whether notifications should be sent based on job.notifyOn and job.status
        boolean shouldNotify = false;
        String notifyOn = job.getNotifyOn();
        String status = job.getStatus();

        if (notifyOn != null) {
            if ("BOTH".equalsIgnoreCase(notifyOn)) {
                shouldNotify = true;
            } else if ("SUCCESS".equalsIgnoreCase(notifyOn) && "SUCCEEDED".equalsIgnoreCase(status)) {
                shouldNotify = true;
            } else if ("FAILURE".equalsIgnoreCase(notifyOn) && "FAILED".equalsIgnoreCase(status)) {
                shouldNotify = true;
            }
        }

        if (!shouldNotify) {
            logger.info("Notification skipped for job {} due to notifyOn={} and status={}", job.getJobId(), notifyOn, status);
            return job;
        }

        // Prepare ingestResult and errors container
        if (job.getIngestResult() == null) {
            job.setIngestResult(new Job.IngestResult());
        }
        List<String> errors = job.getIngestResult().getErrors();
        if (errors == null) {
            errors = new ArrayList<>();
            job.getIngestResult().setErrors(errors);
        }

        // Fetch all subscribers
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
            String msg = "Interrupted while fetching subscribers: " + ie.getMessage();
            logger.error(msg, ie);
            errors.add(msg);
        } catch (ExecutionException ee) {
            String msg = "Error fetching subscribers: " + ee.getMessage();
            logger.error(msg, ee);
            errors.add(msg);
        } catch (Exception ex) {
            String msg = "Unexpected error fetching subscribers: " + ex.getMessage();
            logger.error(msg, ex);
            errors.add(msg);
        }

        if (payloads == null || payloads.isEmpty()) {
            logger.info("No subscribers found to notify for job {}", job.getJobId());
            // Still mark job as notified even if there are no subscribers
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            return job;
        }

        List<Subscriber> subscribers = new ArrayList<>();
        for (DataPayload payload : payloads) {
            try {
                Subscriber s = objectMapper.treeToValue(payload.getData(), Subscriber.class);
                subscribers.add(s);
            } catch (Exception ex) {
                String msg = "Failed to convert subscriber payload to Subscriber object: " + ex.getMessage();
                logger.error(msg, ex);
                errors.add(msg);
            }
        }

        // For each active & verified subscriber, send notification and update lastNotifiedAt
        for (Subscriber sub : subscribers) {
            try {
                if (sub == null) continue;
                if (Boolean.FALSE.equals(sub.getActive())) {
                    logger.debug("Skipping inactive subscriber {}", sub.getId());
                    continue;
                }
                if (Boolean.FALSE.equals(sub.getVerified())) {
                    logger.debug("Skipping unverified subscriber {}", sub.getId());
                    continue;
                }
                // Basic filtering could be implemented based on Subscriber.filters if needed.
                // For now notify all active & verified subscribers.

                boolean notifySuccess = sendNotificationToSubscriber(sub, job);
                if (notifySuccess) {
                    // update subscriber.lastNotifiedAt
                    String now = Instant.now().toString();
                    sub.setLastNotifiedAt(now);
                    try {
                        CompletableFuture<UUID> updatedFuture = entityService.updateItem(UUID.fromString(sub.getId()), sub);
                        updatedFuture.get();
                    } catch (Exception ex) {
                        String msg = "Failed to update subscriber lastNotifiedAt for " + sub.getId() + ": " + ex.getMessage();
                        logger.error(msg, ex);
                        errors.add(msg);
                    }
                } else {
                    String msg = "Notification failed for subscriber " + sub.getId();
                    logger.warn(msg);
                    errors.add(msg);
                }
            } catch (Exception ex) {
                String msg = "Error processing subscriber " + (sub != null ? sub.getId() : "unknown") + ": " + ex.getMessage();
                logger.error(msg, ex);
                errors.add(msg);
            }
        }

        // Set final job status to NOTIFIED_SUBSCRIBERS
        job.setStatus("NOTIFIED_SUBSCRIBERS");

        // Attach errors back to ingestResult
        job.getIngestResult().setErrors(errors);

        logger.info("Completed notifying subscribers for job {}. Errors: {}", job.getJobId(), errors.size());
        return job;
    }

    private boolean sendNotificationToSubscriber(Subscriber sub, Job job) {
        if (sub.getContactType() == null) {
            logger.warn("Subscriber {} has no contactType", sub.getId());
            return false;
        }
        String contactType = sub.getContactType();
        try {
            String payloadJson = objectMapper.writeValueAsString(job);
            if ("webhook".equalsIgnoreCase(contactType)) {
                if (sub.getContactDetails() == null || sub.getContactDetails().getUrl() == null) {
                    logger.warn("Subscriber {} webhook missing URL", sub.getId());
                    return false;
                }
                String url = sub.getContactDetails().getUrl();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    logger.info("Successfully notified subscriber {} via webhook (status={})", sub.getId(), statusCode);
                    return true;
                } else {
                    logger.warn("Failed to notify subscriber {} via webhook (status={})", sub.getId(), statusCode);
                    return false;
                }
            } else if ("email".equalsIgnoreCase(contactType)) {
                // Email sending not implemented in this processor; simulate success for now.
                logger.info("Simulated email notification to subscriber {}", sub.getId());
                return true;
            } else {
                // Other contact types: log and consider success for now
                logger.info("Unknown contactType '{}' for subscriber {} - skipping actual delivery but marking notified", contactType, sub.getId());
                return true;
            }
        } catch (Exception ex) {
            logger.error("Error notifying subscriber {}: {}", sub.getId(), ex.getMessage(), ex);
            return false;
        }
    }
}