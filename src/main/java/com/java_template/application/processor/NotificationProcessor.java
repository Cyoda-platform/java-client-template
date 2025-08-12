package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Notifying subscribers for Job: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid Job entity state for notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Query subscribers interested in the ingested categories
            List<Subscriber> subscribers = querySubscribers(job);

            // Send notifications to subscribers
            for (Subscriber subscriber : subscribers) {
                logger.info("Notifying subscriber: {} via email: {}", subscriber.getSubscriberName(), subscriber.getContactEmail());
                // Implement actual notification sending logic (email, webhook)
                sendNotification(subscriber, job);
            }

            // Update Job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            logger.info("Job status updated to NOTIFIED_SUBSCRIBERS: {}", job.getJobName());

        } catch (Exception e) {
            logger.error("Error sending notifications for Job {}: {}", job.getJobName(), e.getMessage());
        }
        return job;
    }

    private List<Subscriber> querySubscribers(Job job) {
        try {
            // Fetch distinct categories from Laureate entities related to this job
            // Since Job does not directly link laureates, we query all laureates and collect categories
            CompletableFuture<ArrayNode> laureatesFuture = entityService.getItems(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode laureatesArray = laureatesFuture.join();

            // Collect unique categories
            List<String> categories = new ArrayList<>();
            for (int i = 0; i < laureatesArray.size(); i++) {
                JsonNode laureateNode = laureatesArray.get(i);
                String category = laureateNode.path("category").asText(null);
                if (category != null && !category.isEmpty() && !categories.contains(category)) {
                    categories.add(category);
                }
            }

            if (categories.isEmpty()) {
                logger.warn("No laureate categories found for notification");
                return new ArrayList<>();
            }

            // Query subscribers interested in these categories
            List<Condition> conditions = categories.stream()
                .map(cat -> Condition.of("$.subscribedCategories", "LIKE", cat))
                .collect(Collectors.toList());

            SearchConditionRequest conditionRequest = SearchConditionRequest.group("OR", conditions);
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                conditionRequest,
                true
            );

            ArrayNode subscribersArray = subscribersFuture.join();
            List<Subscriber> subscribers = new ArrayList<>();
            for (int i = 0; i < subscribersArray.size(); i++) {
                JsonNode subscriberNode = subscribersArray.get(i);
                Subscriber subscriber = objectMapper.convertValue(subscriberNode, Subscriber.class);
                subscribers.add(subscriber);
            }

            return subscribers;

        } catch (Exception e) {
            logger.error("Error querying subscribers: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        try {
            // Implement email or webhook notification
            if (subscriber.getContactEmail() != null && !subscriber.getContactEmail().isEmpty()) {
                // Simulate sending email
                logger.info("Sending email to {} for Job {}", subscriber.getContactEmail(), job.getJobName());
            }
            if (subscriber.getContactWebhook() != null && !subscriber.getContactWebhook().isEmpty()) {
                // Simulate calling webhook
                logger.info("Calling webhook {} for Job {}", subscriber.getContactWebhook(), job.getJobName());
            }
        } catch (Exception e) {
            logger.error("Failed to send notification to subscriber {}: {}", subscriber.getSubscriberName(), e.getMessage());
        }
    }
}
