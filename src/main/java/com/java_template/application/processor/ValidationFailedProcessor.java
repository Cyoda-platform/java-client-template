package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class ValidationFailedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationFailedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationFailedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // Business logic:
        // - Mark the pet as validation_failed when this processor runs.
        // - Be idempotent: if already in validation_failed state, do nothing.
        // - Update the updatedAt timestamp to reflect the change.
        if (entity == null) {
            return null;
        }

        try {
            String currentStatus = entity.getStatus();
            if (!"validation_failed".equalsIgnoreCase(currentStatus)) {
                logger.info("Setting status of pet {} to validation_failed (was: {})", entity.getId(), currentStatus);
                entity.setStatus("validation_failed");
                try {
                    entity.setUpdatedAt(Instant.now().toString());
                } catch (Exception e) {
                    // If updatedAt setter is not available or fails, log and continue.
                    logger.debug("Could not set updatedAt on pet {}: {}", entity.getId(), e.getMessage());
                }
            } else {
                logger.debug("Pet {} already in validation_failed state; no action taken.", entity.getId());
            }
        } catch (Exception e) {
            logger.error("Error while processing pet {} in ValidationFailedProcessor: {}", entity.getId(), e.getMessage(), e);
            // Do not throw; leave entity as-is so workflow can handle persistence/retries.
        }

        return entity;
    }
}