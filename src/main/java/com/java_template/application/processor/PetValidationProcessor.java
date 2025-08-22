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
public class PetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetValidationProcessor(SerializerFactory serializerFactory) {
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

        try {
            // Idempotency: if already beyond 'new' in canonical workflow, skip re-validation
            String currentStatus = entity.getStatus();
            if (currentStatus != null) {
                String normalized = currentStatus.trim().toLowerCase();
                if ("validated".equals(normalized)
                        || "available".equals(normalized)
                        || "reserved".equals(normalized)
                        || "adopted".equals(normalized)
                        || "archived".equals(normalized)) {
                    logger.info("Pet [{}] is already in status '{}', skipping validation.", entity.getId(), currentStatus);
                    return entity;
                }
            }

            String name = entity.getName();
            String species = entity.getSpecies();

            boolean missingName = (name == null || name.trim().isEmpty());
            boolean missingSpecies = (species == null || species.trim().isEmpty());

            if (missingName || missingSpecies) {
                // mark validation failed when required fields are missing
                entity.setStatus("validation_failed");
                logger.warn("Pet [{}] validation failed. missingName={}, missingSpecies={}", entity.getId(), missingName, missingSpecies);
                // Note: domain events should be emitted by the framework or another component; here we only update state.
                return entity;
            }

            // Basic checks pass -> set to validated
            entity.setStatus("validated");
            logger.info("Pet [{}] validated successfully.", entity.getId());
            return entity;

        } catch (Exception e) {
            // Resilient handling: on unexpected errors mark as validation_failed and log
            try {
                entity.setStatus("validation_failed");
            } catch (Exception ex) {
                logger.error("Failed to set validation_failed status on Pet [{}]", entity != null ? entity.getId() : "unknown", ex);
            }
            logger.error("Unexpected error while validating Pet [{}]: {}", entity != null ? entity.getId() : "unknown", e.getMessage(), e);
            return entity;
        }
    }
}