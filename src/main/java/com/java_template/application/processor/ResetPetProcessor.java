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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ResetPetProcessor - Resets returned pet for re-adoption
 * 
 * This processor handles the reset of a returned pet to make it
 * available for adoption again.
 */
@Component
public class ResetPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ResetPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ResetPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Resetting Pet for re-adoption for request: {}", request.getId());

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
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && "returned".equals(currentState);
    }

    /**
     * Main business logic for resetting pet for re-adoption
     */
    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();

        logger.debug("Resetting pet for re-adoption: {} with ID: {}", pet.getName(), currentEntityId);

        // Log reset activity
        logger.info("Pet {} (ID: {}) has been reset and is available for adoption again", pet.getName(), currentEntityId);

        // Note: The pet entity itself doesn't need modification for reset
        // The state transition to 'available' is handled by the workflow
        // Any medical history updates would be done through separate processes

        return entityWithMetadata;
    }
}
