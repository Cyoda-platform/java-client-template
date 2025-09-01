package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Preserve existing ADOPTED/AVAILABLE statuses
        String currentStatus = entity.getStatus();
        if (currentStatus != null) {
            if (currentStatus.equalsIgnoreCase("ADOPTED") || currentStatus.equalsIgnoreCase("AVAILABLE")) {
                logger.info("Pet {} already in terminal status '{}', skipping validation state changes.", entity.getId(), currentStatus);
                return entity;
            }
        }

        // Additional business validations beyond basic isValid()
        boolean quarantine = false;

        // Species that are unknown or invalid -> quarantine
        String species = entity.getSpecies();
        if (species != null) {
            String s = species.trim();
            if (s.equalsIgnoreCase("unknown") || s.equalsIgnoreCase("n/a") || s.equalsIgnoreCase("none")) {
                quarantine = true;
                logger.warn("Pet {} has unknown species '{}' -> marking QUARANTINE", entity.getId(), species);
            }
        }

        // Implausible age -> quarantine
        Integer age = entity.getAge();
        if (age != null && age > 30) {
            quarantine = true;
            logger.warn("Pet {} has implausible age '{}' -> marking QUARANTINE", entity.getId(), age);
        }

        // Photo presence is desirable but not mandatory for validation.
        // If no photos present, keep validated but log for enrichment step.
        if (entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty()) {
            logger.info("Pet {} has no photos; enrichment may attempt to fetch images.", entity.getId());
        }

        // Vaccinations checked in isValid(); nothing extra here.

        // Set status based on results
        if (quarantine) {
            entity.setStatus("QUARANTINE");
        } else {
            // Mark as VALIDATED to allow subsequent enrichment/availability processors to run
            entity.setStatus("VALIDATED");
        }

        logger.info("Pet {} validation completed. New status={}", entity.getId(), entity.getStatus());
        return entity;
    }
}