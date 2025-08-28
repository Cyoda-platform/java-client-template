package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
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

@Component
public class PetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            // Use business-level validation rather than relying on entity.isValid() which may enforce different constraints.
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Business-level validation for Pet that checks required fields for listing.
     * Note: We intentionally do not require status here because new entities may not have it set and
     * the processor is responsible for assigning the correct workflow status.
     */
    private boolean isValidEntity(Pet entity) {
        if (entity == null) return false;
        if (entity.getName() == null || entity.getName().isBlank()) return false;
        if (entity.getSpecies() == null || entity.getSpecies().isBlank()) return false;
        if (entity.getSex() == null || entity.getSex().isBlank()) return false;
        // age if provided must be non-negative
        if (entity.getAge() != null && entity.getAge() < 0) return false;
        return true;
    }

    /**
     * Applies the Pet validation and state transition rules:
     * - If required data fields missing -> status = "invalid"
     * - If no photos present -> status = "invalid"
     * - Otherwise -> status = "available" (unless already "adopted")
     *
     * The processor updates updatedAt timestamp. Persistence of the entity state is handled by Cyoda workflow.
     */
    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        if (entity == null) {
            logger.error("Received null Pet entity in execution context");
            return null;
        }

        // Re-check required fields defensively (even though validate() ran)
        boolean missingRequired =
            entity.getName() == null || entity.getName().isBlank()
            || entity.getSpecies() == null || entity.getSpecies().isBlank()
            || entity.getSex() == null || entity.getSex().isBlank()
            || (entity.getAge() != null && entity.getAge() < 0);

        if (missingRequired) {
            logger.warn("Pet '{}' failed validation: missing required fields. Marking as invalid.", entity.getId());
            entity.setStatus("invalid");
            entity.setUpdatedAt(Instant.now().toString());
            return entity;
        }

        // Photo criterion: require at least one photo
        boolean hasPhotos = entity.getPhotos() != null && !entity.getPhotos().isEmpty();
        if (!hasPhotos) {
            logger.warn("Pet '{}' failed validation: no photos present. Marking as invalid.", entity.getId());
            entity.setStatus("invalid");
            entity.setUpdatedAt(Instant.now().toString());
            return entity;
        }

        // All checks passed -> set status to available (listed) unless already adopted
        String currentStatus = entity.getStatus();
        if (currentStatus == null || !"adopted".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("available");
        }
        entity.setUpdatedAt(Instant.now().toString());
        logger.info("Pet '{}' validated successfully and marked as '{}'.", entity.getId(), entity.getStatus());

        return entity;
    }
}