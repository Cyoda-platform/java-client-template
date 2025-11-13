package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * ABOUTME: Processor that validates pet data during updates.
 * Ensures data consistency and updates modification timestamps.
 */
@Component
public class ValidatePetDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePetDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidatePetDataProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating pet data for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::validatePetDataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Pet> validatePetDataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Validating pet: {} in state: {}", pet.getPetId(), currentState);

        // Validate pet data
        validatePetFields(pet);

        // Update modification timestamp
        pet.setUpdatedAt(LocalDateTime.now());

        logger.info("Pet {} data validated successfully", pet.getPetId());

        return entityWithMetadata;
    }

    private void validatePetFields(Pet pet) {
        // Validate age is non-negative if provided
        if (pet.getAge() != null && pet.getAge() < 0) {
            logger.warn("Pet {} has invalid age: {}", pet.getPetId(), pet.getAge());
            pet.setAge(null);
        }

        // Trim whitespace from string fields
        if (pet.getName() != null) {
            pet.setName(pet.getName().trim());
        }
        if (pet.getSpecies() != null) {
            pet.setSpecies(pet.getSpecies().trim());
        }
        if (pet.getBreed() != null) {
            pet.setBreed(pet.getBreed().trim());
        }
        if (pet.getColor() != null) {
            pet.setColor(pet.getColor().trim());
        }
        if (pet.getOwnerName() != null) {
            pet.setOwnerName(pet.getOwnerName().trim());
        }

        logger.debug("Pet {} fields validated and normalized", pet.getPetId());
    }
}

