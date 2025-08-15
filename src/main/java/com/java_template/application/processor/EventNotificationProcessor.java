package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.event.version_1.Event;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class EventNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EventNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EventNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Event Notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Event.class)
            .validate(this::isValidEntity, "Invalid event state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Event entity) {
        return entity != null && entity.isValid();
    }

    private Event processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Event> context) {
        Event entity = context.entity();
        logger.info("Notifying subscribers about event: {}", entity.getTitle());

        // Implement notification logic
        // For example, fetch all interested users or subscribers filtering by event category or location
        // Then send notifications (simulate by logging here)

        try {
            // Construct a search condition for subscribers based on event category
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.category", "EQUALS", entity.getCategory())
            );

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                    "Subscriber", // Assuming 'Subscriber' entity exists
                    "1",
                    condition,
                    true
            );

            subscribersFuture.thenAccept(subscribers -> {
                // Simulate notification
                logger.info("Notifying {} subscribers about event: {}", subscribers.size(), entity.getTitle());
            }).join();

        } catch (Exception e) {
            logger.error("Error during notification processing for event {}: {}", entity.getTitle(), e.getMessage(), e);
        }

        return entity;
    }
}
