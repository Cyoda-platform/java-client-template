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
 * Processor for creating new pets in the Purrfect Pets application
 * Handles the initial creation and validation of pet entities
 */
@Component
public class CreatePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreatePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreatePetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::processCreatePetLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for pet creation
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet pet = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return pet != null && pet.isValid() && technicalId != null;
    }

    /**
     * Main business logic for creating a new pet
     * Sets default values and timestamps for the new pet
     */
    private EntityWithMetadata<Pet> processCreatePetLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        logger.debug("Creating new pet: {}", pet.getPetId());

        // Set creation and update timestamps
        LocalDateTime now = LocalDateTime.now();
        pet.setCreatedAt(now);
        pet.setUpdatedAt(now);

        // Set default status if not provided
        if (pet.getStatus() == null || pet.getStatus().trim().isEmpty()) {
            pet.setStatus("available");
        }

        // Validate and normalize status
        String normalizedStatus = normalizeStatus(pet.getStatus());
        pet.setStatus(normalizedStatus);

        // Ensure photoUrls is not null
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new java.util.ArrayList<>());
        }

        // Set default price if not provided
        if (pet.getPrice() == null) {
            pet.setPrice(0.0);
        }

        logger.info("Pet {} created successfully with status: {}", pet.getPetId(), pet.getStatus());

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
