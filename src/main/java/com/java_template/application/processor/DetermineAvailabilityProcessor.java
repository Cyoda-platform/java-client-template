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

@Component
public class DetermineAvailabilityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DetermineAvailabilityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DetermineAvailabilityProcessor(SerializerFactory serializerFactory) {
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

        try {
            // If entity explicitly archived already, leave as-is
            if (entity.getStatus() != null && entity.getStatus().equalsIgnoreCase("ARCHIVED")) {
                logger.info("Pet {} is already archived. No availability change.", entity.getId());
                return entity;
            }

            // Determine enrichment state
            Pet.Metadata metadata = entity.getMetadata();
            boolean enriched = false;
            boolean hasImages = false;
            boolean hasTags = false;

            if (metadata != null) {
                if (metadata.getEnrichedAt() != null && !metadata.getEnrichedAt().isBlank()) {
                    enriched = true;
                }
                if (metadata.getImages() != null && !metadata.getImages().isEmpty()) {
                    hasImages = true;
                }
                if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
                    hasTags = true;
                }
            }

            // Business rules:
            // - If entity is valid and enrichment has completed (enrichedAt present and at least one image),
            //   mark as AVAILABLE.
            // - If enrichment not complete but entity valid, mark as HOLD so it can be reviewed/enriched later.
            // - If entity invalid (should not happen due to validation) mark ARCHIVED as fallback.
            if (!entity.isValid()) {
                logger.warn("Pet {} failed validation during availability check — archiving.", entity.getId());
                entity.setStatus("ARCHIVED");
                return entity;
            }

            if (enriched && hasImages) {
                logger.info("Pet {} enrichment detected (enrichedAt present and images available). Marking AVAILABLE.", entity.getId());
                entity.setStatus("AVAILABLE");
            } else {
                logger.info("Pet {} not fully enriched (enriched={}, hasImages={}, hasTags={}). Marking HOLD.", entity.getId(), enriched, hasImages, hasTags);
                entity.setStatus("HOLD");
            }

        } catch (Exception ex) {
            logger.error("Error while determining availability for Pet {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // On unexpected error, mark as HOLD to avoid accidental listing
            if (entity != null) {
                entity.setStatus("HOLD");
            }
        }

        return entity;
    }
}