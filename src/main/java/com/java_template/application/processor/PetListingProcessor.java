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
public class PetListingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetListingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetListingProcessor(SerializerFactory serializerFactory) {
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

        if (entity == null) {
            logger.warn("Pet entity is null in processing context");
            return entity;
        }

        // Business logic:
        // - Ensure pet has required valid data (isValid() was checked already by validator)
        // - Ensure at least one photo exists; if none, mark status as "invalid"
        // - If valid and has photo(s), set status to "available" (listed)
        // - Set/update timestamps: createdAt if missing, always update updatedAt
        // - Do not perform add/update/delete on the triggering entity via EntityService here;
        //   Cyoda will persist the mutated entity automatically.

        boolean hasPhotos = entity.getPhotos() != null && !entity.getPhotos().isEmpty();

        // Preserve existing terminal statuses (adopted, archived) — do not override them.
        String currentStatus = entity.getStatus() != null ? entity.getStatus().toLowerCase() : null;
        boolean isTerminal = "adopted".equalsIgnoreCase(currentStatus) || "archived".equalsIgnoreCase(currentStatus);

        if (isTerminal) {
            logger.info("Pet {} is in terminal status '{}'; skipping listing changes.", entity.getId(), entity.getStatus());
        } else {
            if (!hasPhotos) {
                logger.info("Pet {} has no photos; marking as 'invalid' for manual correction.", entity.getId());
                entity.setStatus("invalid");
            } else {
                // If all data valid and photos present, list the pet as available
                logger.info("Pet {} passed listing criteria; marking as 'available'.", entity.getId());
                entity.setStatus("available");
            }
        }

        // Timestamps: createdAt (string) and updatedAt (string)
        String now = Instant.now().toString();
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        return entity;
    }
}