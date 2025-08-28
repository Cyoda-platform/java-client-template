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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class ManualFixAction implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ManualFixAction.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    public ManualFixAction(SerializerFactory serializerFactory) {
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
            // Allow manual fix to operate on entities that may be currently invalid;
            // only require a non-null entity to proceed with manual corrections.
            .validate(this::isValidEntity, "Entity is null")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // ManualFixAction business logic:
        // - This processor is intended to be triggered manually by staff after they've corrected
        //   Pet data. It will verify that the pet now meets the core criteria:
        //     * Required fields: name, species, sex (non-blank)
        //     * At least one photo present
        //   If criteria pass, set pet.status -> "available" and update updatedAt timestamp.
        //   If criteria do not pass, leave the entity unchanged (staff should correct and retry).

        if (entity == null) {
            logger.warn("Received null Pet entity in ManualFixAction");
            return null;
        }

        // Check required string fields
        boolean hasName = entity.getName() != null && !entity.getName().isBlank();
        boolean hasSpecies = entity.getSpecies() != null && !entity.getSpecies().isBlank();
        boolean hasSex = entity.getSex() != null && !entity.getSex().isBlank();

        if (!hasName || !hasSpecies || !hasSex) {
            logger.warn("Pet {} is missing required fields. namePresent={}, speciesPresent={}, sexPresent={}",
                    entity.getId(), hasName, hasSpecies, hasSex);
            return entity;
        }

        // Check photos
        boolean hasPhotos = entity.getPhotos() != null && !entity.getPhotos().isEmpty();
        if (!hasPhotos) {
            logger.warn("Pet {} has no photos; cannot mark as available without at least one photo.", entity.getId());
            return entity;
        }

        // All manual-fix criteria satisfied -> set status to available and update timestamp
        String previousStatus = entity.getStatus();
        entity.setStatus("available");
        entity.setUpdatedAt(Instant.now().toString());

        logger.info("Pet {} manual fix applied. status: {} -> {}, updatedAt set to {}",
                entity.getId(), previousStatus, entity.getStatus(), entity.getUpdatedAt());

        return entity;
    }
}