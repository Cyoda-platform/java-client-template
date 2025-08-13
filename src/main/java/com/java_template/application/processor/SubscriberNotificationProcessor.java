package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class SubscriberNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public SubscriberNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Notifying subscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob)
            .map(this::processNotificationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getStatus() != null &&
            (job.getStatus().equalsIgnoreCase("SUCCEEDED") || job.getStatus().equalsIgnoreCase("FAILED"));
    }

    private Job processNotificationLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        try {
            // Fetch active subscribers
            logger.info("Fetching active subscribers for notification");
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.active", "EQUALS", true)
            );
            CompletableFuture<java.util.List<ObjectNode>> futureSubscribers = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );

            List<ObjectNode> subscribers = futureSubscribers.get();
            if (subscribers.isEmpty()) {
                logger.info("No active subscribers found for notification");
            } else {
                // Notify each subscriber
                for (ObjectNode subNode : subscribers) {
                    Subscriber subscriber = objectMapper.convertValue(subNode, Subscriber.class);
                    notifySubscriber(subscriber, job);
                }
            }

            // Update job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            job.setCompletedAt(Instant.now().toString());
            logger.info("Job status updated to NOTIFIED_SUBSCRIBERS");

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            logger.error("Notification interrupted: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to notify subscribers: {}", e.getMessage(), e);
        }

        return job;
    }

    private void notifySubscriber(Subscriber subscriber, Job job) {
        try {
            String contactType = subscriber.getContactType();
            String contactValue = subscriber.getContactValue();
            logger.info("Notifying subscriber {} via {}", subscriber.getSubscriberId(), contactType);

            if ("email".equalsIgnoreCase(contactType)) {
                // Simulate email sending
                logger.info("Sending email to {}: Job '{}' status '{}'", contactValue, job.getJobName(), job.getStatus());
            } else if ("webhook".equalsIgnoreCase(contactType)) {
                // Send webhook notification
                sendWebhookNotification(contactValue, job);
            } else {
                logger.warn("Unknown contact type '{}' for subscriber {}", contactType, subscriber.getSubscriberId());
            }

        } catch (Exception e) {
            logger.error("Failed to notify subscriber {}: {}", subscriber.getSubscriberId(), e.getMessage(), e);
        }
    }

    private void sendWebhookNotification(String url, Job job) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jobName", job.getJobName());
            payload.put("status", job.getStatus());
            payload.put("completedAt", job.getCompletedAt());

            HttpEntity<String> requestEntity = new HttpEntity<>(payload.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("Webhook notification to {} failed with status {}", url, response.getStatusCode());
            } else {
                logger.info("Webhook notification sent successfully to {}", url);
            }
        } catch (Exception e) {
            logger.error("Failed to send webhook notification to {}: {}", url, e.getMessage(), e);
        }
    }
}
