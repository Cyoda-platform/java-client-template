package com.java_template.application.processor;

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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getStatus() != null && !job.getStatus().trim().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        logger.info("Notifying subscribers for Job: {} with status: {}", job.getJobName(), job.getStatus());

        try {
            // Fetch all laureates for this job categories
            // We assume job resultSummary contains categories info or fetch all laureates
            // For this example, we fetch all laureates

            CompletableFuture<ArrayNode> laureatesFuture = entityService.getItems(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode laureates = laureatesFuture.get();

            // Collect distinct categories from laureates
            List<String> categories = new ArrayList<>();
            for (var laur : laureates) {
                String category = laur.path("category").asText(null);
                if (category != null && !categories.contains(category)) {
                    categories.add(category);
                }
            }

            // Fetch all active subscribers
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.active", "EQUALS", true)
            );

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode subscribers = subscribersFuture.get();

            // Filter subscribers by categories
            List<Subscriber> subscribersToNotify = new ArrayList<>();
            for (var subscriberNode : subscribers) {
                String subscribedCategories = subscriberNode.path("subscribedCategories").asText("");
                for (String category : categories) {
                    if (subscribedCategories.toLowerCase().contains(category.toLowerCase())) {
                        Subscriber subscriber = new Subscriber();
                        subscriber.setSubscriberName(subscriberNode.path("subscriberName").asText());
                        subscriber.setContactType(subscriberNode.path("contactType").asText());
                        subscriber.setContactDetails(subscriberNode.path("contactDetails").asText());
                        subscriber.setSubscribedCategories(subscribedCategories);
                        subscriber.setActive(subscriberNode.path("active").asBoolean());
                        subscribersToNotify.add(subscriber);
                        break;
                    }
                }
            }

            // Send notifications (simulate sending, actual implementation depends on contact type)
            for (Subscriber subscriber : subscribersToNotify) {
                logger.info("Notifying subscriber: {} via {}", subscriber.getSubscriberName(), subscriber.getContactType());
                // TODO: Implement actual notification sending (email, webhook, etc.)
            }

            // Update job status
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            job.setEndedAt(ZonedDateTime.now());
            logger.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", job.getJobName());

        } catch (Exception e) {
            logger.error("Failed to notify subscribers", e);
            job.setStatus("FAILED");
            job.setEndedAt(ZonedDateTime.now());
            job.setResultSummary("Failed to notify subscribers: " + e.getMessage());
        }

        return job;
    }
}
