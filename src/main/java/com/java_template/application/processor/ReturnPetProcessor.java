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
 * ReturnPetProcessor - Handles pet return after adoption
 * 
 * This processor handles the return of a pet after adoption,
 * logging the return activity for tracking purposes.
 */
@Component
public class ReturnPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReturnPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReturnPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet return for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null && "adopted".equals(currentState);
    }

    /**
     * Main business logic for processing pet return
     */
    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();

        logger.debug("Processing return for pet: {} with ID: {}", pet.getName(), currentEntityId);

        // Log return activity
        logger.info("Pet {} (ID: {}) has been returned", pet.getName(), currentEntityId);

        // Note: Return details and adoption record updates will be handled by the Adoption entity workflow
        // This processor focuses only on the pet-specific aspects of the return

        return entityWithMetadata;
    }
}
