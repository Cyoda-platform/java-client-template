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
public class LaureateValidationProcessor implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public LaureateValidationProcessor(SerializerFactory serializerFactory) {
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
         // Validation based on business requirements from functional_requirement.md

         if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) {
            return EvaluationOutcome.fail("Firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getSurname() == null || laureate.getSurname().isBlank()) {
            return EvaluationOutcome.fail("Surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getYear() == null || laureate.getYear().isBlank()) {
            return EvaluationOutcome.fail("Year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getCategory() == null || laureate.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Additional possible business logic validation example
         // Check year format (should be four digits)
         if (!laureate.getYear().matches("\\d{4}")) {
            return EvaluationOutcome.fail("Year must be a 4-digit number", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Check gender if present, must be male/female/other (example)
         if (laureate.getGender() != null && !laureate.getGender().isBlank()) {
             String gender = laureate.getGender().toLowerCase();
             if (!(gender.equals("male") || gender.equals("female") || gender.equals("other"))) {
                 return EvaluationOutcome.fail("Gender must be male, female, or other", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}
