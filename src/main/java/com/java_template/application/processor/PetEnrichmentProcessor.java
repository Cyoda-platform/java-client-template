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
public class PetEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetEnrichmentProcessor(SerializerFactory serializerFactory) {
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
        Pet entity = context.entity();
        if (entity == null) {
            logger.warn("Received null Pet entity in PetEnrichmentProcessor");
            return null;
        }

        boolean enriched = false;

        // If sourceId present try to enrich minimal metadata (photos, description)
        String sourceId = entity.getSourceId();
        if (sourceId != null && !sourceId.isBlank()) {
            // If no photos, add a best-effort placeholder derived from source
            try {
                if (entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
                    String photoUrl = buildPhotoUrlFromSource(entity.getSourceUrl(), sourceId);
                    if (photoUrl != null) {
                        entity.getPhotos().add(photoUrl);
                        enriched = true;
                        logger.info("Enriched pet {} with photo from sourceId {}", entity.getId(), sourceId);
                    }
                }

                // If description is empty, populate a short note referencing the source
                if ((entity.getDescription() == null || entity.getDescription().isBlank()) && entity.getSourceUrl() != null && !entity.getSourceUrl().isBlank()) {
                    entity.setDescription("Imported from source: " + entity.getSourceUrl());
                    enriched = true;
                    logger.info("Enriched pet {} with description from sourceUrl {}", entity.getId(), entity.getSourceUrl());
                }
            } catch (Exception e) {
                logger.warn("Failed to enrich pet {} from sourceId {}: {}", entity.getId(), sourceId, e.getMessage());
            }
        }

        // Ensure pet has a usable status after enrichment step; default to "available" if not provided
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("available");
            logger.info("Set default status 'available' for pet {}", entity.getId());
            enriched = true;
        }

        if (!enriched) {
            logger.debug("No enrichment applied for pet {}", entity.getId());
        }

        return entity;
    }

    private String buildPhotoUrlFromSource(String sourceUrl, String sourceId) {
        // Best-effort generation of a representative photo URL.
        // Do not call external services here — just infer a plausible URL if sourceUrl is present.
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            // Attempt to append a standard path if the sourceUrl looks like a base URL
            String base = sourceUrl.endsWith("/") ? sourceUrl.substring(0, sourceUrl.length() - 1) : sourceUrl;
            return base + "/photos/" + sourceId;
        }
        // If no sourceUrl, produce a generic placeholder URL that references the sourceId
        if (sourceId != null && !sourceId.isBlank()) {
            return "https://petstore.example/assets/pets/" + sourceId + "/photo";
        }
        return null;
    }
}