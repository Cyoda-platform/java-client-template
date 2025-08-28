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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private static final String CRITERION_NAME = "ValidationCriterion";

    public ValidationCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name
        return modelSpec != null && CRITERION_NAME.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Required fields per entity contract
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // year should be a 4-digit number (basic sanity check)
         String year = entity.getYear();
         try {
             int y = Integer.parseInt(year);
             if (year.length() != 4 || y < 0) {
                 return EvaluationOutcome.fail("year must be a 4-digit positive number", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (NumberFormatException ex) {
             return EvaluationOutcome.fail("year must be numeric", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // born and died date format validation if present
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
             try {
                 LocalDate.parse(entity.getBorn());
             } catch (DateTimeParseException ex) {
                 return EvaluationOutcome.fail("born must be ISO date yyyy-MM-dd", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }
         if (entity.getDied() != null && !entity.getDied().isBlank()) {
             try {
                 LocalDate.parse(entity.getDied());
             } catch (DateTimeParseException ex) {
                 return EvaluationOutcome.fail("died must be ISO date yyyy-MM-dd or null", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // ageAtAward quality check
         if (entity.getAgeAtAward() != null && entity.getAgeAtAward() < 0) {
             return EvaluationOutcome.fail("ageAtAward must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // validated field, if present, must be VALIDATED or INVALID
         if (entity.getValidated() != null && !entity.getValidated().isBlank()) {
             String v = entity.getValidated();
             if (!(v.equalsIgnoreCase("VALIDATED") || v.equalsIgnoreCase("INVALID"))) {
                 return EvaluationOutcome.fail("validated must be either 'VALIDATED' or 'INVALID' if provided", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // bornCountryCode should be 2-letter ISO if provided
         if (entity.getBornCountryCode() != null && !entity.getBornCountryCode().isBlank()) {
             String code = entity.getBornCountryCode();
             if (code.length() != 2) {
                 return EvaluationOutcome.fail("bornCountryCode should be a 2-letter country code", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}