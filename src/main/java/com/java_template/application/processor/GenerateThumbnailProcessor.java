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

import java.util.ArrayList;
import java.util.List;

@Component
public class GenerateThumbnailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateThumbnailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public GenerateThumbnailProcessor(SerializerFactory serializerFactory) {
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

        // Business logic:
        // Generate thumbnail URLs for each image URL and attach them in a non-destructive way.
        // The Pet entity model in this project does not contain an explicit imageThumbs field,
        // therefore we append generated thumbnail URLs to the images list after validating we
        // are not duplicating existing thumbnail entries. This keeps thumbnails alongside images
        // so downstream processors or consumers can discover them until a proper imageThumbs
        // property is added to the model.

        List<String> images = entity.getImages();

        if (images == null || images.isEmpty()) {
            logger.info("No images present for pet id={}, skipping thumbnail generation", entity.getId());
            return entity;
        }

        // Prepare a set of existing images for duplicate checks (preserve original list order)
        List<String> existing = new ArrayList<>(images);
        List<String> thumbnailsToAdd = new ArrayList<>();

        for (String url : images) {
            if (url == null || url.isBlank()) {
                logger.debug("Skipping blank image entry for pet id={}", entity.getId());
                continue;
            }

            // Simple heuristic: consider URLs that contain "-thumb" or "?thumb=true" already thumbnails
            boolean isAlreadyThumb = url.contains("-thumb") || url.contains("?thumb=true") || url.contains("thumb.");
            if (isAlreadyThumb) {
                logger.debug("Image already a thumbnail (skipping generation): {} for pet id={}", url, entity.getId());
                continue;
            }

            String thumb = createThumbnailUrl(url);
            // Avoid duplicates
            if (!existing.contains(thumb) && !thumbnailsToAdd.contains(thumb)) {
                thumbnailsToAdd.add(thumb);
            }
        }

        if (!thumbnailsToAdd.isEmpty()) {
            List<String> combined = new ArrayList<>(existing);
            combined.addAll(thumbnailsToAdd);
            entity.setImages(combined);
            logger.info("Generated {} thumbnails for pet id={}", thumbnailsToAdd.size(), entity.getId());
        } else {
            logger.info("No new thumbnails generated for pet id={}", entity.getId());
        }

        return entity;
    }

    private String createThumbnailUrl(String originalUrl) {
        // Lightweight deterministic thumbnail URL generator (no external calls).
        // Strategy: if URL has an extension, insert "-thumb" before the extension.
        // Otherwise append "?thumb=true" to create a unique thumb URL.
        try {
            int qIdx = originalUrl.indexOf('?');
            String base = qIdx >= 0 ? originalUrl.substring(0, qIdx) : originalUrl;
            String query = qIdx >= 0 ? originalUrl.substring(qIdx) : "";

            int lastDot = base.lastIndexOf('.');
            if (lastDot > base.lastIndexOf('/')) {
                // has extension
                String before = base.substring(0, lastDot);
                String ext = base.substring(lastDot); // includes dot
                return before + "-thumb" + ext + query;
            } else {
                // no extension
                return originalUrl + (query.isEmpty() ? "?thumb=true" : "&thumb=true");
            }
        } catch (Exception e) {
            // Fallback: append a safe suffix
            return originalUrl + "-thumb";
        }
    }
}