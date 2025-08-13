package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(com.java_template.application.entity.notification.version_1.Notification.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(com.java_template.application.entity.notification.version_1.Notification entity) {
        return entity != null && entity.isValid();
    }

    private com.java_template.application.entity.notification.version_1.Notification processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<com.java_template.application.entity.notification.version_1.Notification> context) {
        com.java_template.application.entity.notification.version_1.Notification notification = context.entity();

        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );

            ArrayNode subscriberNodes = subscribersFuture.get();
            if (subscriberNodes != null) {
                for (JsonNode node : subscriberNodes) {
                    Subscriber subscriber = serializer.deserializeEntity(node, Subscriber.class);
                    // Simulate sending notification
                    logger.info("Sending notification to subscriber: {} with message: {}",
                        subscriber.getEmail(), notification.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to process notifications", e);
        }

        return notification;
    }
}
