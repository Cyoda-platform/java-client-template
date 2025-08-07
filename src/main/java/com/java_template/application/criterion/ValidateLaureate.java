package com.java_template.application.criterion;

import com.java_template.application.entity.Laureate;
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
public class ValidateLaureate implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateLaureate(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request)
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate laureate = context.entity();
         // Validate required string fields for non-null and non-blank
         if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) {
            return EvaluationOutcome.fail("Firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getSurname() == null || laureate.getSurname().isBlank()) {
            return EvaluationOutcome.fail("Surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getGender() == null || laureate.getGender().isBlank()) {
            return EvaluationOutcome.fail("Gender is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getBorn() == null || laureate.getBorn().isBlank()) {
            return EvaluationOutcome.fail("Born date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getBorncountry() == null || laureate.getBorncountry().isBlank()) {
            return EvaluationOutcome.fail("Born country is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getYear() == null || laureate.getYear().isBlank()) {
            return EvaluationOutcome.fail("Year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getCategory() == null || laureate.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate gender allowed values
         String gender = laureate.getGender().toLowerCase();
         if (!gender.equals("male") && !gender.equals("female") && !gender.equals("other")) {
            return EvaluationOutcome.fail("Gender must be 'male', 'female', or 'other'", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate year is numeric and plausible
         try {
            int year = Integer.parseInt(laureate.getYear());
            if (year < 1900 || year > 2100) {
                return EvaluationOutcome.fail("Year must be between 1900 and 2100", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
         } catch (NumberFormatException e) {
            return EvaluationOutcome.fail("Year must be a valid number", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate calculatedAge if present must be non-negative
         if (laureate.getCalculatedAge() != null && laureate.getCalculatedAge() < 0) {
            return EvaluationOutcome.fail("Calculated age cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
