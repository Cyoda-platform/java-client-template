package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;

@Component
public class DeleteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeleteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeleteProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
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

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        // Business logic for deleting a Subscriber:
        // - Mark the subscriber as inactive so they won't receive further notifications.
        // - Clear channels to avoid accidental deliveries.
        // - Record deletion time in lastNotifiedAt to indicate when the deletion occured.
        try {
            if (entity == null) {
                logger.warn("Received null Subscriber entity in DeleteProcessor");
                return entity;
            }

            logger.info("Deleting subscriber: subscriberId={}", entity.getSubscriberId());

            // If already inactive, no-op but ensure channels are cleared for safety
            Boolean active = entity.getActive();
            if (active != null && !active) {
                logger.info("Subscriber {} is already inactive", entity.getSubscriberId());
            }

            // Mark as inactive
            entity.setActive(false);

            // Clear channels to prevent further deliveries
            entity.setChannels(new ArrayList<>());

            // Set deletion timestamp in lastNotifiedAt to indicate the time of deletion
            entity.setLastNotifiedAt(Instant.now().toString());

            logger.info("Subscriber {} marked as deleted (inactive).", entity.getSubscriberId());
        } catch (Exception ex) {
            logger.error("Error while processing delete for Subscriber {}: {}", 
                entity != null ? entity.getSubscriberId() : "unknown", ex.getMessage(), ex);
            // On error, propagate by returning the entity unchanged; serializer will handle response
        }

        return entity;
    }
}