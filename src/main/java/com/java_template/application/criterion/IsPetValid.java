package com.java_template.application.criterion;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IsPetValid implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsPetValid(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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

        Pet pet = context.entity();

        // Implement validation logic based on business requirements
        if (pet.getName() == null || pet.getName().isBlank()) {
            return EvaluationOutcome.fail("Name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isBlank()) {
            return EvaluationOutcome.fail("Photo URLs are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getTags() == null || pet.getTags().isBlank()) {
            return EvaluationOutcome.fail("Tags are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getCreatedAt() == null || pet.getCreatedAt().isBlank()) {
            return EvaluationOutcome.fail("Creation date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Business rule: status must be one of available, pending, sold
        String status = pet.getStatus().toLowerCase();
        if (!status.equals("available") && !status.equals("pending") && !status.equals("sold")) {
            return EvaluationOutcome.fail("Invalid status value", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
