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
 * Processor for updating existing pets in the Purrfect Pets application
 * Handles general pet information updates while maintaining status
 */
@Component
public class UpdatePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdatePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdatePetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::processUpdatePetLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for pet update
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet pet = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return pet != null && pet.isValid() && technicalId != null;
    }

    /**
     * Main business logic for updating an existing pet
     * Updates timestamps and validates data consistency
     */
    private EntityWithMetadata<Pet> processUpdatePetLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        logger.debug("Updating pet: {}", pet.getPetId());

        // Update timestamp
        pet.setUpdatedAt(LocalDateTime.now());

        // Validate and normalize status if provided
        if (pet.getStatus() != null) {
            String normalizedStatus = normalizeStatus(pet.getStatus());
            pet.setStatus(normalizedStatus);
        }

        // Ensure photoUrls is not null
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new java.util.ArrayList<>());
        }

        // Validate price is not negative
        if (pet.getPrice() != null && pet.getPrice() < 0) {
            pet.setPrice(0.0);
        }

        // Validate age is not negative
        if (pet.getAge() != null && pet.getAge() < 0) {
            pet.setAge(0);
        }

        logger.info("Pet {} updated successfully", pet.getPetId());

        return entityWithMetadata;
    }

    /**
     * Normalizes pet status to valid values
     */
    private String normalizeStatus(String status) {
        if (status == null) {
            return "available";
        }
        
        String lowerStatus = status.toLowerCase().trim();
        return switch (lowerStatus) {
            case "available", "pending", "sold" -> lowerStatus;
            default -> "available";
        };
    }
}
