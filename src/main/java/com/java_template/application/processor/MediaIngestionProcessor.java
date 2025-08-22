package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
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

@Component
public class MediaIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MediaIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MediaIngestionProcessor(SerializerFactory serializerFactory) {
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
        Pet entity = context.entity();

        // If there are no photos, keep the pet in a created state (media not present)
        if (entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
            logger.info("Pet {} has no photos, leaving media processing state as 'created'", entity.getId());
            // Use available status values on the Pet entity only; do not invent new fields.
            // Keep or set to 'created' to indicate media not present/processed.
            entity.setStatus("created");
            return entity;
        }

        // Mark as processing_media while we validate/process images
        logger.info("Pet {} media ingestion started ({} photos)", entity.getId(), entity.getPhotos().size());
        entity.setStatus("processing_media");

        for (String url : entity.getPhotos()) {
            try {
                if (!isValidImageUrl(url)) {
                    logger.error("Media processing failed for pet {} on url '{}': invalid URL/format", entity.getId(), url);
                    // On any failure, mark pet as unavailable for publish until media issues resolved
                    entity.setStatus("unavailable");
                    return entity;
                }
                // Simulate thumbnail generation/processing step (no external calls)
                generateThumbnail(url);
            } catch (Exception e) {
                logger.error("Media processing exception for pet {} on url '{}': {}", entity.getId(), url, e.getMessage(), e);
                entity.setStatus("unavailable");
                return entity;
            }
        }

        // All media items processed successfully
        entity.setStatus("media_processed");
        logger.info("Pet {} media ingestion completed successfully", entity.getId());
        return entity;
    }

    // Basic validation for image URLs (no network calls): non-blank, starts with http/https and common image extensions
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lc = url.trim().toLowerCase();
        if (!(lc.startsWith("http://") || lc.startsWith("https://"))) return false;
        return lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") || lc.endsWith(".gif") || lc.endsWith(".webp") || lc.endsWith(".bmp");
    }

    // Placeholder for thumbnail generation. No-op but kept to indicate processing step.
    private void generateThumbnail(String url) {
        // Intentionally left blank (simulate processing). Any exceptions would bubble up and mark media as failed.
    }
}