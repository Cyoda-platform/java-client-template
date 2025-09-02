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

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;

@Component
public class PetValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidationCriterion(SerializerFactory serializerFactory) {
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

        // Pet name validation
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Pet name is required and must be 1-100 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getName().length() < 1 || entity.getName().length() > 100) {
            return EvaluationOutcome.fail("Pet name is required and must be 1-100 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Photo URLs validation
        if (entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty()) {
            return EvaluationOutcome.fail("Pet must have at least one photo URL", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        for (String photoUrl : entity.getPhotoUrls()) {
            if (!isValidUrl(photoUrl)) {
                return EvaluationOutcome.fail("Invalid photo URL format: " + photoUrl, 
                    StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Price validation
        if (entity.getPrice() != null && entity.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return EvaluationOutcome.fail("Pet price must be positive", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Birth date validation
        if (entity.getBirthDate() != null && entity.getBirthDate().isAfter(LocalDate.now())) {
            return EvaluationOutcome.fail("Pet birth date cannot be in the future", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Weight validation
        if (entity.getWeight() != null && entity.getWeight() <= 0) {
            return EvaluationOutcome.fail("Pet weight must be positive", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Category validation
        if (entity.getCategory() == null) {
            return EvaluationOutcome.fail("Pet category is required and must be active", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!Boolean.TRUE.equals(entity.getCategory().getActive())) {
            return EvaluationOutcome.fail("Pet category is required and must be active", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Tags validation
        if (entity.getTags() != null) {
            for (Pet.Tag tag : entity.getTags()) {
                if (!Boolean.TRUE.equals(tag.getActive())) {
                    return EvaluationOutcome.fail("All pet tags must be active", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        }

        // Description validation
        if (entity.getDescription() != null && entity.getDescription().length() > 1000) {
            return EvaluationOutcome.fail("Pet description cannot exceed 1000 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
