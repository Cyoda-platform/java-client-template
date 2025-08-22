package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class PetValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Required fields
        String name = entity.getName();
        if (name == null || name.trim().isEmpty()) {
            return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        String species = entity.getSpecies();
        if (species == null || species.trim().isEmpty()) {
            return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Age consistency
        Number age = entity.getAge();
        if (age != null) {
            try {
                double ageVal = age.doubleValue();
                if (ageVal < 0) {
                    return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            } catch (Exception e) {
                return EvaluationOutcome.fail("age must be a number", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Gender normalization / allowed values if present
        String gender = entity.getGender();
        if (gender != null) {
            Set<String> allowedGenders = Set.of("male", "female", "unknown");
            if (!allowedGenders.contains(gender.toLowerCase())) {
                return EvaluationOutcome.fail("gender must be one of: male, female, unknown", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Status allowed values if present
        String status = entity.getStatus();
        if (status != null) {
            Set<String> allowedStatuses = Set.of(
                "new", "validated", "available", "reserved", "adopted", "archived", "validation_failed"
            );
            if (!allowedStatuses.contains(status.toLowerCase())) {
                return EvaluationOutcome.fail("status has an unknown value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Photos quality checks: if provided, entries must be non-empty
        List<String> photos = entity.getPhotos();
        if (photos != null) {
            for (String p : photos) {
                if (p == null || p.trim().isEmpty()) {
                    return EvaluationOutcome.fail("photo entries must be non-empty URLs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }
        }

        // All basic validations passed
        return EvaluationOutcome.success();
    }
}