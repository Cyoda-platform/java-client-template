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
public class PublishPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PublishPetProcessor(SerializerFactory serializerFactory) {
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
            logger.warn("PublishPetProcessor invoked with null entity in context");
            return null;
        }

        String currentStatus = entity.getStatus() != null ? entity.getStatus().trim().toLowerCase() : "";
        String petId = entity.getId();

        // Terminal or reservation-related states should not be overridden by publish logic
        if ("adopted".equalsIgnoreCase(currentStatus)
                || "reserved".equalsIgnoreCase(currentStatus)
                || "held".equalsIgnoreCase(currentStatus)) {
            logger.info("Pet {} is in terminal/reservation state '{}'; skipping publish logic", petId, currentStatus);
            return entity;
        }

        boolean hasPhotos = entity.getPhotos() != null && !entity.getPhotos().isEmpty();
        boolean healthOk = entity.getHealthNotes() == null || entity.getHealthNotes().isBlank();

        // Business rules derived from functional requirements:
        // - A pet can be made 'available' only when media is present (photos) and health checks pass.
        // - If health issues are present, set to 'sick'.
        // - If media is missing, mark as 'unavailable' (so it won't be published).
        if (hasPhotos && healthOk) {
            if (!"available".equalsIgnoreCase(currentStatus)) {
                entity.setStatus("available");
                logger.info("Pet {} published: status set to 'available'", petId);
            } else {
                logger.debug("Pet {} already 'available'; no status change", petId);
            }
        } else {
            if (!healthOk) {
                // If health notes indicate issues, mark as sick so it won't be published
                if (!"sick".equalsIgnoreCase(currentStatus)) {
                    entity.setStatus("sick");
                    logger.info("Pet {} not published: health issues present, status set to 'sick'", petId);
                }
            } else if (!hasPhotos) {
                // No media present - cannot publish
                if (!"unavailable".equalsIgnoreCase(currentStatus)) {
                    entity.setStatus("unavailable");
                    logger.info("Pet {} not published: missing media, status set to 'unavailable'", petId);
                }
            }
        }

        // Note: updatedAt and mediaStatus fields are not present on the Pet entity model in this codebase,
        // so we cannot set them here. Persistence of the changed Pet will be handled by Cyoda automatically.
        return entity;
    }
}