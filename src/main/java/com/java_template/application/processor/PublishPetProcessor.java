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
        logger.info("Processing Pet publish for request: {}", request.getId());

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

        if (entity == null) {
            logger.warn("PublishPetProcessor invoked with null entity");
            return null;
        }

        String currentStatus = entity.getStatus() != null ? entity.getStatus().trim().toLowerCase() : "";

        // Determine media readiness using existing fields:
        // Since Pet model in this project does not include mediaStatus, we infer readiness from photos presence.
        boolean hasPhotos = entity.getPhotos() != null && !entity.getPhotos().isEmpty();
        // Health check: if healthNotes contain content assume potential issues; empty/null means no known issues.
        boolean healthOk = entity.getHealthNotes() == null || entity.getHealthNotes().isBlank();

        try {
            // Only publish if pet is not already in a terminal/reserved/adopted state.
            boolean isTerminalOrReserved = "adopted".equalsIgnoreCase(currentStatus)
                    || "reserved".equalsIgnoreCase(currentStatus)
                    || "held".equalsIgnoreCase(currentStatus);

            if (!isTerminalOrReserved && hasPhotos && healthOk) {
                // Set to available when criteria satisfied
                entity.setStatus("available");
                logger.info("Pet [{}] published: status set to 'available'", entity.getId());
            } else {
                // Keep existing state. If health notes indicate sickness, preserve or log.
                if (!healthOk) {
                    logger.info("Pet [{}] not published due to health notes: {}", entity.getId(), entity.getHealthNotes());
                } else if (!hasPhotos) {
                    logger.info("Pet [{}] not published - no photos found", entity.getId());
                } else {
                    logger.debug("Pet [{}] publish skipped - current status '{}'", entity.getId(), currentStatus);
                }
            }
        } catch (Exception ex) {
            logger.error("Error while processing publish logic for Pet [{}]: {}", entity.getId(), ex.getMessage(), ex);
            // Do not throw - return entity so serializer can complete; Cyoda will handle persistence.
        }

        return entity;
    }
}