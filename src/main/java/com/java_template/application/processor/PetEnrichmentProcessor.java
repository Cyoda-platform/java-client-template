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
        // If a sourceId is present, attempt a minimal enrichment:
        // - If no photos are present and sourceUrl is available, add the sourceUrl as a photo
        // - If description is empty, populate with a minimal enriched description
        // - Ensure status is set to "available" if it's not already set (enrichment step completes availability)
        try {
            String sourceId = entity.getSourceId();
            boolean hasSource = sourceId != null && !sourceId.isBlank();

            if (hasSource) {
                // Enrich photos using sourceUrl when photos missing
                if ((entity.getPhotos() == null || entity.getPhotos().isEmpty())) {
                    String sourceUrl = entity.getSourceUrl();
                    if (sourceUrl != null && !sourceUrl.isBlank()) {
                        entity.getPhotos().add(sourceUrl);
                        logger.info("PetEnrichmentProcessor: added sourceUrl as photo for pet id={}", entity.getId());
                    }
                }

                // Enrich description if missing
                if (entity.getDescription() == null || entity.getDescription().isBlank()) {
                    String enrichedDesc = "Enriched from source: " + sourceId;
                    if (entity.getSourceUrl() != null && !entity.getSourceUrl().isBlank()) {
                        enrichedDesc += " (" + entity.getSourceUrl() + ")";
                    }
                    entity.setDescription(enrichedDesc);
                    logger.info("PetEnrichmentProcessor: set description for pet id={}", entity.getId());
                }

                // Ensure status set to available if not provided
                if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                    entity.setStatus("available");
                    logger.info("PetEnrichmentProcessor: set status=available for pet id={}", entity.getId());
                }
            } else {
                // No sourceId -> ensure at least a default status if missing to keep entity valid
                if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                    entity.setStatus("available");
                    logger.info("PetEnrichmentProcessor: no sourceId, set default status=available for pet id={}", entity.getId());
                }
            }
        } catch (Exception ex) {
            logger.error("PetEnrichmentProcessor: error during enrichment for pet id=" + entity.getId(), ex);
            // Do not throw; return entity as-is to let workflow decide further actions
        }

        return entity;
    }
}