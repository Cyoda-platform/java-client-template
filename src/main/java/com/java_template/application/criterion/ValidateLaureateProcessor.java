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
public class ValidateLaureateProcessor implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateLaureateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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

         // Validate required fields as per entity's isValid method
         if (laureate.getLaureateId() == null || laureate.getLaureateId().isBlank()) {
            return EvaluationOutcome.fail("Laureate ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) {
            return EvaluationOutcome.fail("First name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
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
         if (laureate.getBornCountry() == null || laureate.getBornCountry().isBlank()) {
            return EvaluationOutcome.fail("Born country is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getBornCountryCode() == null || laureate.getBornCountryCode().isBlank()) {
            return EvaluationOutcome.fail("Born country code is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getBornCity() == null || laureate.getBornCity().isBlank()) {
            return EvaluationOutcome.fail("Born city is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getYear() == null || laureate.getYear().isBlank()) {
            return EvaluationOutcome.fail("Year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getCategory() == null || laureate.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Additional business rule example: year must be numeric and >= 1901 (first Nobel Prize year)
         try {
             int yearNum = Integer.parseInt(laureate.getYear());
             if (yearNum < 1901) {
                return EvaluationOutcome.fail("Year must be 1901 or later", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         } catch (NumberFormatException e) {
             return EvaluationOutcome.fail("Year must be a valid number", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optional: Validate gender values
         if (!("male".equalsIgnoreCase(laureate.getGender()) || "female".equalsIgnoreCase(laureate.getGender()) || "other".equalsIgnoreCase(laureate.getGender()))) {
             return EvaluationOutcome.fail("Gender must be male, female, or other", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
