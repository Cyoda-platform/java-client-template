package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.WeeklyCatFactJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class WeeklyCatFactJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public WeeklyCatFactJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyCatFactJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(WeeklyCatFactJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyCatFactJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyCatFactJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyCatFactJob> context) {
        WeeklyCatFactJob job = context.entity();
        UUID technicalId = context.request().getEntityId();

        // Validate subscriber email
        if (job.getSubscriberEmail() == null || job.getSubscriberEmail().isBlank()) {
            job.setStatus("FAILED");
            logger.error("WeeklyCatFactJob {} validation failed: subscriberEmail blank", technicalId);
            return job;
        }

        try {
            // Check if subscriber exists
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.email", "IEQUALS", job.getSubscriberEmail()));
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Subscriber", "1", condition, true);
            ArrayNode subscribers = filteredItemsFuture.get();

            if (subscribers == null || subscribers.size() == 0) {
                // Add new subscriber
                Subscriber subscriber = new Subscriber();
                subscriber.setEmail(job.getSubscriberEmail());
                subscriber.setSubscribedAt(java.time.Instant.now().toString());
                CompletableFuture<UUID> idFuture = entityService.addItem("Subscriber", "1", subscriber);
                UUID subscriberId = idFuture.get();
                processSubscriber(subscriberId, subscriber);
                logger.info("New subscriber created during WeeklyCatFactJob processing with id {}", subscriberId);
            }

            // Call Cat Fact API
            String apiUrl = "https://catfact.ninja/fact";
            Map response = context.request().getRestTemplate().getForObject(apiUrl, Map.class);
            if (response != null && response.containsKey("fact")) {
                String fact = response.get("fact").toString();
                job.setCatFact(fact);
                job.setStatus("PROCESSING");

                // Send email to all subscribers (simulate)
                CompletableFuture<ArrayNode> allSubscribersFuture = entityService.getItems("Subscriber", "1");
                ArrayNode allSubscribers = allSubscribersFuture.get();
                if (allSubscribers != null) {
                    for (int i = 0; i < allSubscribers.size(); i++) {
                        ObjectNode subscriberNode = (ObjectNode) allSubscribers.get(i);
                        String email = subscriberNode.has("email") ? subscriberNode.get("email").asText() : null;
                        if (email != null) {
                            logger.info("Sending cat fact email to {}", email);
                            // Here would be email sending logic
                        }
                    }
                }

                job.setStatus("COMPLETED");
                logger.info("WeeklyCatFactJob {} completed successfully", technicalId);
            } else {
                job.setStatus("FAILED");
                logger.error("Cat Fact API did not return a fact");
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            logger.error("Error in WeeklyCatFactJob processing: {}", e.getMessage());
        }

        return job;
    }

    private void processSubscriber(UUID technicalId, Subscriber subscriber) {
        if (subscriber.getEmail() == null || subscriber.getEmail().isBlank() || !subscriber.getEmail().contains("@")) {
            logger.error("Subscriber {} has invalid email format", technicalId);
            return;
        }
        // Could add confirmation email logic here
        logger.info("Subscriber {} processed successfully", technicalId);
    }

}
