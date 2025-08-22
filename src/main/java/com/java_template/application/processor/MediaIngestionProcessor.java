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

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        try {
            // If there are no photos, nothing to process — leave entity state as-is.
            if (pet.getPhotos() == null || pet.getPhotos().isEmpty()) {
                logger.info("Pet {} has no photos - skipping media ingestion", pet.getId());
                return pet;
            }

            // Mark the pet as processing media using the status field (mediaStatus not present on entity)
            logger.info("Pet {} - setting status to processing_media", pet.getId());
            pet.setStatus("processing_media");

            // Process each photo URL with simple validation and simulated thumbnail generation.
            for (String url : pet.getPhotos()) {
                if (url == null || url.isBlank()) {
                    logger.error("Pet {} - found blank photo URL, failing media processing", pet.getId());
                    pet.setStatus("unavailable"); // set to an appropriate failure state available on entity
                    pet.setHealthNotes((pet.getHealthNotes() == null ? "" : pet.getHealthNotes() + " | ")
                            + "Media processing failed: blank photo URL");
                    return pet;
                }

                // Basic URL validation - ensure it looks like an HTTP/HTTPS resource.
                String lower = url.toLowerCase();
                if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                    logger.error("Pet {} - invalid photo URL '{}' - failing media processing", pet.getId(), url);
                    pet.setStatus("unavailable");
                    pet.setHealthNotes((pet.getHealthNotes() == null ? "" : pet.getHealthNotes() + " | ")
                            + "Media processing failed: invalid photo URL");
                    return pet;
                }

                // Simulate processing: in a real implementation we'd call image validation/generation services.
                logger.debug("Pet {} - validated photo URL '{}'", pet.getId(), url);
            }

            // If all photos validated/processed successfully, mark media as processed by using status
            logger.info("Pet {} - media processed successfully, setting status to media_processed", pet.getId());
            pet.setStatus("media_processed");

        } catch (Exception ex) {
            logger.error("Exception while processing media for pet {}: {}", pet != null ? pet.getId() : "unknown", ex.getMessage(), ex);
            if (pet != null) {
                pet.setStatus("unavailable");
                pet.setHealthNotes((pet.getHealthNotes() == null ? "" : pet.getHealthNotes() + " | ")
                        + "Media processing exception: " + ex.getMessage());
            }
        }

        return pet;
    }
}