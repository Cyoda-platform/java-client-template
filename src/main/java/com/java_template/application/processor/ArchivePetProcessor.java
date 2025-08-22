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

import java.time.Instant;

@Component
public class ArchivePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchivePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchivePetProcessor(SerializerFactory serializerFactory) {
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
        return entity != null && entity.getId() != null && !entity.getId().trim().isEmpty();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        try {
            String currentStatus = entity.getStatus();
            if (currentStatus != null && "archived".equalsIgnoreCase(currentStatus)) {
                logger.info("Pet {} is already archived. No action taken.", entity.getId());
                return entity;
            }

            // Business rule: Archive the pet as a terminal state.
            // This processor is used for manual or automatic cleanup. Ensure idempotency:
            // - If already archived, do nothing.
            // - Otherwise set status to "archived" and update timestamp.
            logger.info("Archiving pet {}. Current status: {}", entity.getId(), currentStatus);
            entity.setStatus("archived");

            // Update the updatedAt timestamp to current time in ISO-8601 format.
            try {
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception e) {
                // If updatedAt setter is not present or fails, log but continue.
                logger.debug("Unable to set updatedAt for pet {}: {}", entity.getId(), e.getMessage());
            }

            // Related metadata archiving: keep minimal changes here to remain safe with unknown POJO shape.
            // Higher-impact operations (like modifying other entities) should be performed via EntityService externally.
            logger.info("Pet {} archived successfully.", entity.getId());
        } catch (Exception ex) {
            logger.error("Error while archiving pet {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw; let the framework handle retries. Return entity to persist any partial changes.
        }

        return entity;
    }
}