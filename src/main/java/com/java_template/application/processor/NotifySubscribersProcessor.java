package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
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

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Ensure summary exists
        if (job.getSummary() == null) {
            Job.Summary summary = new Job.Summary();
            summary.setErrors(new ArrayList<>());
            summary.setFailedCount(0);
            summary.setIngestedCount(0);
            job.setSummary(summary);
        } else {
            if (job.getSummary().getErrors() == null) {
                job.getSummary().setErrors(new ArrayList<>());
            }
            if (job.getSummary().getFailedCount() == null) {
                job.getSummary().setFailedCount(0);
            }
            if (job.getSummary().getIngestedCount() == null) {
                job.getSummary().setIngestedCount(0);
            }
        }

        List<DataPayload> dataPayloads;
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                Subscriber.ENTITY_VERSION,
                null, null, null
            );
            dataPayloads = itemsFuture.get();
        } catch (Exception ex) {
            String msg = "Failed to load subscribers: " + ex.getMessage();
            logger.error(msg, ex);
            job.getSummary().getErrors().add(msg);
            job.setState("NOTIFIED_SUBSCRIBERS");
            job.setCompletedTimestamp(Instant.now().toString());
            return job;
        }

        if (dataPayloads == null || dataPayloads.isEmpty()) {
            logger.info("No subscribers found to notify for job id {}", job.getId());
            job.setState("NOTIFIED_SUBSCRIBERS");
            job.setCompletedTimestamp(Instant.now().toString());
            return job;
        }

        HttpClient httpClient = HttpClient.newHttpClient();

        for (DataPayload payload : dataPayloads) {
            try {
                Subscriber subscriber = objectMapper.treeToValue(payload.getData(), Subscriber.class);
                if (subscriber == null) {
                    logger.warn("Encountered null subscriber payload");
                    continue;
                }
                if (!Boolean.TRUE.equals(subscriber.getActive())) {
                    continue; // skip inactive subscribers
                }

                String contactType = subscriber.getContactType();
                String contactDetail = subscriber.getContactDetail();

                // Prepare notification body - send the job summary and basic job metadata
                NotificationBody body = new NotificationBody();
                body.jobId = job.getId();
                body.state = job.getState();
                body.runTimestamp = job.getRunTimestamp();
                body.completedTimestamp = job.getCompletedTimestamp();
                body.summary = job.getSummary();

                String bodyJson = objectMapper.writeValueAsString(body);

                if ("webhook".equalsIgnoreCase(contactType)) {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(contactDetail))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                            .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        int status = response.statusCode();
                        if (status < 200 || status >= 300) {
                            String err = "Failed webhook notification to " + contactDetail + " status=" + status;
                            logger.error(err);
                            job.getSummary().getErrors().add(err);
                            job.getSummary().setFailedCount(job.getSummary().getFailedCount() + 1);
                        } else {
                            logger.info("Successfully notified webhook subscriber {} for job {}", subscriber.getId(), job.getId());
                        }
                    } catch (Exception e) {
                        String err = "Exception calling webhook " + contactDetail + " : " + e.getMessage();
                        logger.error(err, e);
                        job.getSummary().getErrors().add(err);
                        job.getSummary().setFailedCount(job.getSummary().getFailedCount() + 1);
                    }
                } else if ("email".equalsIgnoreCase(contactType)) {
                    // No SMTP configured here - simulate/send asynchronously by logging
                    try {
                        // Simulate success - in a real implementation you'd integrate with an email service
                        logger.info("Simulated email to {} with payload: {}", contactDetail, bodyJson);
                    } catch (Exception e) {
                        String err = "Failed sending email to " + contactDetail + " : " + e.getMessage();
                        logger.error(err, e);
                        job.getSummary().getErrors().add(err);
                        job.getSummary().setFailedCount(job.getSummary().getFailedCount() + 1);
                    }
                } else {
                    String err = "Unknown contactType for subscriber " + subscriber.getId() + ": " + contactType;
                    logger.warn(err);
                    job.getSummary().getErrors().add(err);
                    job.getSummary().setFailedCount(job.getSummary().getFailedCount() + 1);
                }
            } catch (Exception ex) {
                String err = "Failed to process subscriber payload: " + ex.getMessage();
                logger.error(err, ex);
                job.getSummary().getErrors().add(err);
                job.getSummary().setFailedCount(job.getSummary().getFailedCount() + 1);
            }
        }

        // Finalize job state to NOTIFIED_SUBSCRIBERS
        job.setState("NOTIFIED_SUBSCRIBERS");
        job.setCompletedTimestamp(Instant.now().toString());

        return job;
    }

    // Simple inner class used to structure notification payload
    private static class NotificationBody {
        public String jobId;
        public String state;
        public String runTimestamp;
        public String completedTimestamp;
        public Job.Summary summary;
    }
}