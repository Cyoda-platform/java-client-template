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
import java.util.ArrayList;
import java.util.List;

@Component
public class PublishPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PublishPetProcessor(SerializerFactory serializerFactory,
                               EntityService entityService,
                               ObjectMapper objectMapper) {
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
        if (entity == null) {
            logger.warn("Received null Pet entity in processing context");
            return null;
        }

        logger.info("PublishPetProcessor executing business logic for pet id: {}, current status: {}",
                entity.getId(), entity.getStatus());

        // 1. Ensure basic defaults for optional fields so Pet is in a consistent state
        if (entity.getHealthNotes() == null || entity.getHealthNotes().isBlank()) {
            entity.setHealthNotes("Not specified");
            logger.debug("Set default healthNotes for pet id {}", entity.getId());
        }

        if (entity.getSize() == null || entity.getSize().isBlank()) {
            entity.setSize("unknown");
            logger.debug("Set default size for pet id {}", entity.getId());
        }

        // 2. Ensure tags list exists
        List<String> tags = entity.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
            entity.setTags(tags);
        }

        // 3. Validate image presence / quality simple heuristic:
        boolean photosOk = false;
        List<String> photos = entity.getPhotos();
        if (photos != null && !photos.isEmpty()) {
            photosOk = true;
            for (String p : photos) {
                if (p == null || p.isBlank()) {
                    photosOk = false;
                    break;
                }
            }
        }

        // If no photos or invalid photos, tag the pet to indicate images needed
        if (!photosOk) {
            if (!tags.contains("needs_images")) {
                tags.add("needs_images");
            }
            logger.info("Pet id {} missing valid photos; tagging with 'needs_images' and not publishing as AVAILABLE", entity.getId());
        }

        // 4. Only transition to AVAILABLE when the pet has completed image processing stage
        String currentStatus = entity.getStatus();
        if (currentStatus != null && currentStatus.equalsIgnoreCase("IMAGES_READY") && photosOk) {
            entity.setStatus("AVAILABLE");
            logger.info("Pet id {} transitioned from IMAGES_READY to AVAILABLE", entity.getId());
        } else {
            // If already marked available by admin or other flows, leave as-is.
            if (currentStatus == null || currentStatus.isBlank()) {
                // If status missing but photos are present, set to AVAILABLE (safe fallback)
                if (photosOk) {
                    entity.setStatus("AVAILABLE");
                    logger.info("Pet id {} had empty status but has photos; setting status to AVAILABLE", entity.getId());
                } else {
                    // Keep status as-is (or mark as ENRICHED if that's appropriate)
                    entity.setStatus(entity.getStatus()); // no-op to emphasize no external update
                    logger.debug("Pet id {} status unchanged (no valid status and no photos)", entity.getId());
                }
            } else {
                logger.debug("Pet id {} status not eligible for publish transition: {}", entity.getId(), currentStatus);
            }
        }

        // 5. Ensure importedAt present: if missing, set to now (ISO-8601)
        if (entity.getImportedAt() == null || entity.getImportedAt().isBlank()) {
            String now = Instant.now().toString();
            entity.setImportedAt(now);
            logger.debug("Set importedAt for pet id {} to {}", entity.getId(), now);
        }

        // 6. Final touch: remove duplicate/blank tags
        List<String> cleanedTags = new ArrayList<>();
        for (String t : entity.getTags()) {
            if (t != null) {
                String trimmed = t.trim();
                if (!trimmed.isBlank() && !cleanedTags.contains(trimmed)) {
                    cleanedTags.add(trimmed);
                }
            }
        }
        entity.setTags(cleanedTags);

        // The processor must not perform add/update/delete on the triggering entity via EntityService.
        // The changed entity will be persisted by the Cyoda workflow automatically.
        return entity;
    }
}