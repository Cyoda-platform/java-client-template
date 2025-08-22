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
public class ProcessPetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessPetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessPetValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(entity -> entity != null, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        if (entity == null) return false;
        String name = entity.getName();
        String species = entity.getSpecies();
        return name != null && !name.trim().isEmpty() && species != null && !species.trim().isEmpty();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();
        if (entity == null) {
            logger.warn("Received null Pet entity in processing context");
            return null;
        }

        String petId = null;
        try {
            petId = entity.getId();
        } catch (Exception ignore) {
            // ignore if getter not present; used only for logging when available
        }

        // Business logic: validate required fields (name and species)
        boolean missingName = entity.getName() == null || entity.getName().trim().isEmpty();
        boolean missingSpecies = entity.getSpecies() == null || entity.getSpecies().trim().isEmpty();

        if (missingName || missingSpecies) {
            // mark as validation_failed when required fields missing
            try {
                entity.setStatus("validation_failed");
            } catch (Exception e) {
                logger.error("Failed to set status on Pet {}: {}", petId, e.getMessage());
            }
            logger.warn("Pet validation failed for id {}. missingName={}, missingSpecies={}", petId, missingName, missingSpecies);
            // According to workflow, an event PetValidationFailed would be emitted here by the platform/eventing layer.
            return entity;
        }

        // Basic checks passed -> mark validated
        try {
            entity.setStatus("validated");
        } catch (Exception e) {
            logger.error("Failed to set status on Pet {}: {}", petId, e.getMessage());
        }
        logger.info("Pet validated successfully for id {}", petId);

        return entity;
    }
}