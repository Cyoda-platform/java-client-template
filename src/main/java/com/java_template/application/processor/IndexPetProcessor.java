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
public class IndexPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexPetProcessor(SerializerFactory serializerFactory) {
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

        // Defensive check
        if (entity == null) {
            logger.warn("IndexPetProcessor received null entity in context");
            return null;
        }

        // If entity is not valid according to its own validation, archive it.
        if (!entity.isValid()) {
            logger.info("Pet {} is invalid - archiving", entity.getId());
            entity.setStatus("ARCHIVED");
            return entity;
        }

        String currentStatus = entity.getStatus() != null ? entity.getStatus().trim() : "";

        // Business rules:
        // - Only pets that are AVAILABLE should be moved to LISTED (i.e., indexed).
        // - Pets on HOLD or other transient statuses are left unchanged.
        // - Invalid pets are archived (handled above).
        // - When a pet is successfully indexed (moved to LISTED), add an "indexed" tag in metadata.tags.

        if ("AVAILABLE".equalsIgnoreCase(currentStatus)) {
            logger.info("Pet {} is AVAILABLE - moving to LISTED and marking as indexed", entity.getId());
            entity.setStatus("LISTED");

            // Ensure metadata exists and tags list exists, then add "indexed" tag if missing
            Pet.Metadata metadata = entity.getMetadata();
            if (metadata == null) {
                metadata = new Pet.Metadata();
            }
            List<String> tags = metadata.getTags();
            if (tags == null) {
                tags = new ArrayList<>();
            }
            boolean hasIndexed = false;
            for (String t : tags) {
                if ("indexed".equalsIgnoreCase(t)) {
                    hasIndexed = true;
                    break;
                }
            }
            if (!hasIndexed) {
                tags.add("indexed");
            }
            // Do not overwrite enrichedAt if present; if absent, set to current time to mark processing time
            if (metadata.getEnrichedAt() == null || metadata.getEnrichedAt().isBlank()) {
                metadata.setEnrichedAt(Instant.now().toString());
            }
            metadata.setTags(tags);
            entity.setMetadata(metadata);

            return entity;
        }

        // If status is HOLD, we do not index; leave status as-is but annotate metadata with processing timestamp
        if ("HOLD".equalsIgnoreCase(currentStatus)) {
            logger.info("Pet {} is on HOLD - skipping indexing", entity.getId());
            Pet.Metadata metadata = entity.getMetadata();
            if (metadata == null) {
                metadata = new Pet.Metadata();
            }
            // Add a processing tag to indicate it was evaluated
            List<String> tags = metadata.getTags();
            if (tags == null) {
                tags = new ArrayList<>();
            }
            boolean hasEvaluated = false;
            for (String t : tags) {
                if ("evaluated".equalsIgnoreCase(t)) {
                    hasEvaluated = true;
                    break;
                }
            }
            if (!hasEvaluated) {
                tags.add("evaluated");
            }
            metadata.setTags(tags);
            // do not change enrichedAt
            entity.setMetadata(metadata);
            return entity;
        }

        // For any other statuses (e.g., already LISTED or ARCHIVED), leave unchanged but log
        logger.info("Pet {} status '{}' does not require indexing actions", entity.getId(), currentStatus);
        return entity;
    }
}