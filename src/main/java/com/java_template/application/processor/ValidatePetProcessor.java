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
import java.util.Objects;

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
        if (entity == null) return null;

        // Ensure photos list is non-null
        if (entity.getPhotos() == null) {
            entity.setPhotos(new ArrayList<>());
        }

        // Normalize string fields (trim) if present
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getSpecies() != null) {
            entity.setSpecies(entity.getSpecies().trim());
        }
        if (entity.getBreed() != null) {
            entity.setBreed(entity.getBreed().trim());
        }
        if (entity.getDescription() != null) {
            entity.setDescription(entity.getDescription().trim());
        }
        if (entity.getGender() != null) {
            entity.setGender(entity.getGender().trim());
        }
        if (entity.getStatus() != null) {
            entity.setStatus(entity.getStatus().trim());
        }

        // Business rule: negative age is invalid — clear and mark invalid
        Integer age = entity.getAge();
        if (age != null && age < 0) {
            entity.setAge(null);
            entity.setStatus("invalid");
            logger.warn("Pet {} had negative age; cleared and marked invalid", entity.getId());
            return entity;
        }

        // If entity already invalid by status, keep it
        String currentStatus = entity.getStatus();
        if (currentStatus != null && currentStatus.equalsIgnoreCase("invalid")) {
            logger.info("Pet {} is explicitly marked as invalid; skipping further validation adjustments", entity.getId());
            return entity;
        }

        // If entity passes isValid(), and status is not set or is a placeholder, mark as available
        boolean hasStatus = currentStatus != null && !currentStatus.isBlank();
        if (entity.isValid() && !hasStatus) {
            entity.setStatus("available");
            logger.info("Pet {} validated and set to status '{}'", entity.getId(), entity.getStatus());
            return entity;
        }

        // If entity not fully valid (but reached here because of serializer flow), mark invalid
        if (!entity.isValid()) {
            entity.setStatus("invalid");
            logger.info("Pet {} missing required fields; marked invalid", entity.getId());
        } else {
            logger.debug("Pet {} remains in status '{}'", entity.getId(), entity.getStatus());
        }

        return entity;
    }
}