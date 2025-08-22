package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
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
        Pet pet = context.entity();
        if (pet == null) {
            logger.warn("Received null Pet in ValidationFailedProcessor");
            return null;
        }

        String currentStatus = pet.getStatus();
        // Only process pets that are in validation_failed state
        if (currentStatus == null || !currentStatus.equalsIgnoreCase("validation_failed")) {
            logger.info("Pet {} not in validation_failed state (current: {}), skipping", pet.getId(), currentStatus);
            return pet;
        }

        // Determine failure reasons based on required fields and photos
        boolean missingName = pet.getName() == null || pet.getName().trim().isEmpty();
        boolean missingSpecies = pet.getSpecies() == null || pet.getSpecies().trim().isEmpty();
        boolean hasPhotos = pet.getPhotos() != null && !pet.getPhotos().isEmpty();

        // Business policy:
        // - If required fields (name or species) are missing, archive the pet automatically.
        // - If only photos are missing or other non-critical issues, leave status as validation_failed for manual review.
        if (missingName || missingSpecies) {
            logger.info("Pet {} missing required fields (name/species). Archiving.", pet.getId());
            pet.setStatus("archived");
            // Emit an informational log which represents a domain event emission step.
            logger.info("Emitting domain event: PetArchived due to validation failure for petId={}", pet.getId());
        } else if (!hasPhotos) {
            logger.info("Pet {} failed validation due to missing photos. Leaving in validation_failed for manual review.", pet.getId());
            // Optionally, could add metadata about reason; since only existing getters/setters may be used,
            // we do not invent fields. Persistence will record status and other existing fields.
            logger.info("Emitting domain event: PetValidationFailed (requires manual review) for petId={}", pet.getId());
        } else {
            // Fallback: if we reach here, keep the pet in validation_failed but log details.
            logger.info("Pet {} remains in validation_failed state. No automatic archival rule matched.", pet.getId());
            logger.info("Emitting domain event: PetValidationFailed for petId={}", pet.getId());
        }

        return pet;
    }
}