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
public class PetInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Set<String> ALLOWED_STATUSES = Set.of(
        "new", "validated", "available", "reserved", "adopted", "archived", "validation_failed"
    );

    private static final Set<String> ALLOWED_GENDERS = Set.of(
        "male", "female", "unknown"
    );

    public PetInvalidCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entity();
        if (pet == null) {
            logger.debug("Pet entity is null in evaluation context");
            return EvaluationOutcome.fail("pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Required fields: name, species
        if (pet.getName() == null || pet.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getSpecies() == null || pet.getSpecies().trim().isEmpty()) {
            return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Age must be non-negative if provided
        try {
            Number ageVal = null;
            // Support both Integer/Long/Double wrappers depending on entity implementation
            Object ageObj = null;
            try {
                ageObj = pet.getAge();
            } catch (Throwable t) {
                // if getter absent or different type fall through; but avoid inventing properties
                ageObj = null;
            }
            if (ageObj instanceof Number) {
                ageVal = (Number) ageObj;
            }
            if (ageVal != null && ageVal.doubleValue() < 0) {
                return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        } catch (Throwable ignored) {
            // If age getter not present or unexpected type, do not fail here; other criteria will catch missing required fields.
        }

        // Gender should be one of allowed values if provided
        if (pet.getGender() != null && !pet.getGender().trim().isEmpty()) {
            String gender = pet.getGender().trim().toLowerCase();
            if (!ALLOWED_GENDERS.contains(gender)) {
                return EvaluationOutcome.fail("gender must be one of: male, female, unknown", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Status, if present, must be one of canonical statuses
        if (pet.getStatus() != null && !pet.getStatus().trim().isEmpty()) {
            String status = pet.getStatus().trim();
            boolean allowed = false;
            for (String s : ALLOWED_STATUSES) {
                if (s.equals(status)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return EvaluationOutcome.fail("status has invalid value", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Photos: basic quality checks - non-empty and look like URLs
        try {
            Object photosObj = pet.getPhotos();
            if (photosObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> photos = (List<Object>) photosObj;
                for (Object p : photos) {
                    if (p == null) {
                        return EvaluationOutcome.fail("photo entry is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                    }
                    String url = p.toString().trim();
                    if (url.isEmpty()) {
                        return EvaluationOutcome.fail("photo url is empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                    }
                    String lower = url.toLowerCase();
                    if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                        return EvaluationOutcome.fail("photo url appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                    }
                }
            }
        } catch (Throwable ignored) {
            // If getPhotos not present or not a List, skip photo checks rather than inventing properties.
        }

        // If all checks pass, entity is considered valid for this "invalid" criterion
        return EvaluationOutcome.success();
    }
}