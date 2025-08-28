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
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class EnrichPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EnrichPetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

        // Normalize textual fields
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getSpecies() != null) {
            entity.setSpecies(entity.getSpecies().trim().toLowerCase());
        }
        if (entity.getBreed() != null) {
            entity.setBreed(entity.getBreed().trim());
        }

        // Ensure metadata exists and lists are initialized
        if (entity.getMetadata() == null) {
            Pet.Metadata metadata = new Pet.Metadata();
            metadata.setImages(new ArrayList<>());
            metadata.setTags(new ArrayList<>());
            entity.setMetadata(metadata);
        } else {
            if (entity.getMetadata().getImages() == null) {
                entity.getMetadata().setImages(new ArrayList<>());
            }
            if (entity.getMetadata().getTags() == null) {
                entity.getMetadata().setTags(new ArrayList<>());
            }
        }

        // Enrichment: set enrichedAt timestamp
        entity.getMetadata().setEnrichedAt(Instant.now().toString());

        // Add automatic tags based on available data (avoid duplicates)
        List<String> tags = entity.getMetadata().getTags();
        if (entity.getSpecies() != null && !entity.getSpecies().isBlank()) {
            String speciesTag = entity.getSpecies().toLowerCase();
            if (!tags.contains(speciesTag)) tags.add(speciesTag);
        }
        if (entity.getBreed() != null && !entity.getBreed().isBlank()) {
            String breedTag = entity.getBreed().toLowerCase();
            if (!tags.contains(breedTag)) tags.add(breedTag);
        }
        if (entity.getMetadata().getImages() != null && !entity.getMetadata().getImages().isEmpty()) {
            if (!tags.contains("has-image")) tags.add("has-image");
        }

        // Determine availability/status
        // Business rule: if entity is invalid mark as ARCHIVED (validation already passed here),
        // otherwise set to AVAILABLE by default for listing.
        // If status is already explicitly set to a non-empty value that indicates adoption or archive, keep it.
        String currentStatus = entity.getStatus();
        if (currentStatus == null || currentStatus.isBlank() || currentStatus.equalsIgnoreCase("PERSISTED")) {
            entity.setStatus("AVAILABLE");
        } else {
            // normalize known statuses
            String normalized = currentStatus.trim().toUpperCase();
            if (normalized.equals("AVAILABLE") || normalized.equals("ADOPTED") || normalized.equals("ARCHIVED") || normalized.equals("HOLD")) {
                // keep original but normalized to conventional casing (lowercase for consistency)
                entity.setStatus(normalized.equals("ADOPTED") ? "ADOPTED"
                        : normalized.equals("ARCHIVED") ? "ARCHIVED"
                        : normalized.equals("HOLD") ? "HOLD"
                        : "AVAILABLE");
            } else {
                // unknown status -> mark as AVAILABLE
                entity.setStatus("AVAILABLE");
            }
        }

        // Note: We must not perform add/update/delete on the triggering Pet via EntityService.
        // Any changes to this entity will be persisted by the Cyoda workflow automatically.

        return entity;
    }
}