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

import java.util.ArrayList;
import java.util.List;

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

        // Collect validation errors
        List<String> errors = new ArrayList<>();

        // Ensure collections are non-null as Pet.isValid expects non-null lists
        if (entity.getPhotoUrls() == null) {
            entity.setPhotoUrls(new ArrayList<>());
        }
        if (entity.getTags() == null) {
            entity.setTags(new ArrayList<>());
        }

        // Validate required business fields
        if (entity.getName() == null || entity.getName().isBlank()) {
            errors.add("Missing or blank name");
        }

        if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
            errors.add("Missing or blank species");
        }

        // photoUrls must contain at least one non-blank URL
        if (entity.getPhotoUrls().isEmpty()) {
            errors.add("photoUrls must contain at least one URL");
        } else {
            boolean anyBlank = false;
            for (String url : entity.getPhotoUrls()) {
                if (url == null || url.isBlank()) {
                    anyBlank = true;
                    break;
                }
            }
            if (anyBlank) {
                errors.add("photoUrls contains blank entries");
            }
        }

        // Tags: ensure non-null entries; if empty it's acceptable
        boolean tagsInvalid = false;
        for (String t : entity.getTags()) {
            if (t == null || t.isBlank()) {
                tagsInvalid = true;
                break;
            }
        }
        if (tagsInvalid) {
            errors.add("tags contains blank entries");
        }

        // Apply validation outcome to entity state
        if (!errors.isEmpty()) {
            // Mark as failed validation
            entity.setStatus("FAILED");
            String existingDesc = entity.getDescription() != null ? entity.getDescription() : "";
            String joined = String.join("; ", errors);
            String newDesc = (existingDesc.isBlank() ? "" : existingDesc + " | ") + "Validation failed: " + joined;
            entity.setDescription(newDesc);
            logger.info("Pet validation failed for petId={} errors={}", entity.getPetId(), joined);
        } else {
            // Validation passed -> set a validation marker/status for downstream processors
            // Keep existing status if it's meaningful; otherwise set to VALIDATED to indicate success
            String currentStatus = entity.getStatus();
            if (currentStatus == null || currentStatus.isBlank() || "PERSISTED_BY_PROCESS".equalsIgnoreCase(currentStatus)) {
                entity.setStatus("VALIDATED");
            } else {
                // append a short note if needed but avoid overwriting explicit statuses like AVAILABLE/ADOPTED
                entity.setStatus(currentStatus);
            }
            logger.info("Pet validation passed for petId={}", entity.getPetId());
        }

        return entity;
    }
}