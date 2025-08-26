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
public class HealthCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HealthCheckProcessor(SerializerFactory serializerFactory) {
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
        Pet pet = context.entity();

        // Basic health and quality checks based on functional requirements:
        // - If healthNotes missing or quality deemed low -> set status to MANUAL_REVIEW
        // - If quality passes -> set status to AVAILABLE
        // Quality heuristics:
        //   * healthNotes must be present and non-blank
        //   * avatarUrl should be present (visual confirmation)
        //   * tags should have at least one entry
        //   * age, if present, should be within a reasonable non-negative range
        boolean needsManualReview = false;

        // Check health notes
        if (pet.getHealthNotes() == null || pet.getHealthNotes().isBlank()) {
            needsManualReview = true;
            // annotate healthNotes so reviewers have context
            pet.setHealthNotes("REVIEW_REQUIRED: health notes missing");
        }

        // Check avatar quality (presence)
        if (pet.getAvatarUrl() == null || pet.getAvatarUrl().isBlank()) {
            needsManualReview = true;
        }

        // Check tags (at least one tag is preferred for publish quality)
        if (pet.getTags() == null || pet.getTags().isEmpty()) {
            needsManualReview = true;
        }

        // Check age validity if present
        if (pet.getAge() != null) {
            Integer age = pet.getAge();
            if (age < 0) {
                needsManualReview = true;
            } else {
                // extreme age values may indicate bad data
                if (age > 25) {
                    needsManualReview = true;
                }
            }
        } else {
            // Age absent is not necessarily fatal, but treat as lower quality
            needsManualReview = true;
        }

        // Finalize status based on checks.
        // Only change status field; Cyoda will persist the entity state automatically.
        if (needsManualReview) {
            pet.setStatus("MANUAL_REVIEW");
            logger.info("Pet {} flagged for manual review by HealthCheckProcessor", pet.getId());
        } else {
            pet.setStatus("AVAILABLE");
            logger.info("Pet {} passed health checks and set to AVAILABLE", pet.getId());
        }

        return pet;
    }
}