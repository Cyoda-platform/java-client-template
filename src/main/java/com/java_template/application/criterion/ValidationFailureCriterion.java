package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

@Component
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             logger.debug("Laureate entity is null in ValidationFailureCriterion");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the validation processor already marked the entity as INVALID, propagate that reason.
         String status = entity.getValidationStatus();
         if (status != null) {
             String normalized = status.trim();
             if (normalized.equalsIgnoreCase("INVALID") || normalized.toUpperCase().startsWith("INVALID:")) {
                 // Use the validationStatus as the failure message so reasons are preserved
                 return EvaluationOutcome.fail(normalized, StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Apply concrete validation rules based on entity fields (use only existing getters)

         // id is required
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("missing id", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // firstname is required and must not be blank
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("missing firstname", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // surname is required and must not be blank
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("missing surname", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // category is required
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("missing category", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // year is required and should be a non-blank string (format validation is not performed here)
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("missing year", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If all checks pass, succeed
         return EvaluationOutcome.success();
    }
}