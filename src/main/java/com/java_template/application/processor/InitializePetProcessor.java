package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;

/**
 * InitializePetProcessor - Validates pet information and sets initial availability
 * 
 * Purpose: Set up pet for adoption availability
 * Transition: initial_state -> available (automatic)
 */
@Component
public class InitializePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InitializePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public InitializePetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet initialization for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     * Validates pet health status and sets arrival date
     */
    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet entity = entityWithMetadata.entity();

        logger.debug("Initializing pet: {}", entity.getPetId());

        // Validate pet health status is "healthy" for adoption availability
        if (!"healthy".equalsIgnoreCase(entity.getHealthStatus())) {
            logger.warn("Pet {} has health status '{}', not 'healthy'", 
                       entity.getPetId(), entity.getHealthStatus());
        }

        // Set arrival date to current timestamp if not already set
        if (entity.getArrivalDate() == null) {
            entity.setArrivalDate(LocalDateTime.now());
            logger.info("Set arrival date for pet {} to {}", 
                       entity.getPetId(), entity.getArrivalDate());
        }

        logger.info("Pet {} initialized successfully", entity.getPetId());

        return entityWithMetadata;
    }
}
