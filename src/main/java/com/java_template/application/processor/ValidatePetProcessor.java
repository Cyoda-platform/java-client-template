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
public class ValidatePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidatePetProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) return false;
        // Business validation for workflow entry:
        // A Pet must have at least a name and species to be considered valid for processing.
        if (entity.getName() == null || entity.getName().isBlank()) return false;
        if (entity.getSpecies() == null || entity.getSpecies().isBlank()) return false;
        // photos list must be non-null (can be empty)
        if (entity.getPhotos() == null) return false;
        // age if present must be non-negative
        if (entity.getAge() != null && entity.getAge() < 0) return false;
        return true;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // Ensure photos is non-null (business rule: photos must be present, can be empty)
        if (entity.getPhotos() == null) {
            entity.setPhotos(new ArrayList<>());
        }

        // Ensure age is non-negative; if invalid, clear it and mark as invalid
        Integer age = entity.getAge();
        if (age != null && age < 0) {
            entity.setAge(null);
            entity.setStatus("invalid");
            logger.warn("Pet {} had negative age; cleared and marked invalid", entity.getId());
            return entity;
        }

        // Main validation: name and species must be present (already checked in validate),
        // but double-check and set status accordingly.
        boolean hasName = entity.getName() != null && !entity.getName().isBlank();
        boolean hasSpecies = entity.getSpecies() != null && !entity.getSpecies().isBlank();

        if (!hasName || !hasSpecies) {
            entity.setStatus("invalid");
            logger.info("Pet {} is missing required fields; marked invalid", entity.getId());
            return entity;
        }

        // If the pet is valid, set its workflow status to 'available' if not already set to a meaningful value.
        String currentStatus = entity.getStatus();
        if (currentStatus == null || currentStatus.isBlank() || "created".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("available");
        }

        // Additional light normalization: trim name/species/breed if present
        if (entity.getName() != null) entity.setName(entity.getName().trim());
        if (entity.getSpecies() != null) entity.setSpecies(entity.getSpecies().trim());
        if (entity.getBreed() != null) entity.setBreed(entity.getBreed().trim());

        logger.info("Pet {} validated and set to status '{}'", entity.getId(), entity.getStatus());

        return entity;
    }
}