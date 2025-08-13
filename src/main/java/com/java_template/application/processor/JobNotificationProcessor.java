package com.java_template.application.processor;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class JobNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public JobNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notification for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(this::isValidEntity, "Invalid Job entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        // Job must be in SUCCEEDED or FAILED state to notify subscribers
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        if (!"SUCCEEDED".equalsIgnoreCase(job.getStatus()) && !"FAILED".equalsIgnoreCase(job.getStatus())) {
            logger.error("Job status is not suitable for notification: {}", job.getStatus());
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // 1. Fetch active subscribers
        SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.active", "EQUALS", true)
        );

        CompletableFuture<ArrayNode> futureSubscribers = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
        );

        try {
            ArrayNode subscribersArray = futureSubscribers.get();
            for (int i = 0; i < subscribersArray.size(); i++) {
                ObjectNode subscriberNode = (ObjectNode) subscribersArray.get(i);
                Subscriber subscriber = entityService.convertNodeToEntity(subscriberNode, Subscriber.class);
                // Send notification to subscriber
                sendNotification(subscriber, job);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to fetch or notify subscribers: {}", e.getMessage());
            job.setMessage("Failed to notify some subscribers: " + e.getMessage());
        }

        // Update job status and completedAt timestamp
        job.setStatus("NOTIFIED_SUBSCRIBERS");
        job.setCompletedAt(Instant.now().toString());
        logger.info("Job notifications sent successfully.");

        return job;
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        if (subscriber == null || !Boolean.TRUE.equals(subscriber.getActive())) {
            logger.warn("Skipping inactive or null subscriber");
            return;
        }

        logger.info("Sending notification to subscriber: {} with contact type: {}", subscriber.getContactValue(), subscriber.getContactType());

        // Implement actual notification logic here based on contactType
        // For example, send email or webhook call
        switch (subscriber.getContactType().toLowerCase()) {
            case "email":
                // Send email notification
                // Placeholder: log instead of sending email
                logger.info("Email sent to {} for job status {}", subscriber.getContactValue(), job.getStatus());
                break;
            case "webhook":
                // Send webhook notification
                sendWebhookNotification(subscriber.getContactValue(), job);
                break;
            default:
                logger.warn("Unknown contact type {} for subscriber {}", subscriber.getContactType(), subscriber.getContactValue());
        }
    }

    private void sendWebhookNotification(String webhookUrl, Job job) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String payload = String.format("{\"jobId\": \"%s\", \"status\": \"%s\"}", job.getApiUrl(), job.getStatus());
            byte[] out = payload.getBytes();
            conn.getOutputStream().write(out);

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("Webhook notification sent successfully to {}", webhookUrl);
            } else {
                logger.error("Failed webhook notification to {} with response code {}", webhookUrl, responseCode);
            }
        } catch (IOException e) {
            logger.error("Exception sending webhook notification to {}: {}", webhookUrl, e.getMessage());
        }
    }
}
