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
public class IsValidLaureateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidLaureateCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Required fields: id, firstname or surname, category, year
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         boolean firstnameMissing = (entity.getFirstname() == null || entity.getFirstname().isBlank());
         boolean surnameMissing = (entity.getSurname() == null || entity.getSurname().isBlank());
         if (firstnameMissing && surnameMissing) {
             return EvaluationOutcome.fail("either firstname or surname must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         } else {
             // year must be a valid integer (basic numeric check)
             try {
                 int y = Integer.parseInt(entity.getYear());
                 if (y < 1000 || y > 9999) {
                     return EvaluationOutcome.fail("year is not a valid 4-digit year", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             } catch (NumberFormatException ex) {
                 return EvaluationOutcome.fail("year must be numeric", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If born is provided, it must be a valid ISO date
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
             try {
                 LocalDate.parse(entity.getBorn());
             } catch (DateTimeParseException ex) {
                 return EvaluationOutcome.fail("born date must be ISO-8601 (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If motivation is present, it must not be blank
         if (entity.getMotivation() != null && entity.getMotivation().isBlank()) {
             return EvaluationOutcome.fail("motivation, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic data quality check: borncountrycode, if provided, should be 2-letter country code
         if (entity.getBorncountrycode() != null && !entity.getBorncountrycode().isBlank()) {
             String code = entity.getBorncountrycode();
             if (code.length() != 2) {
                 return EvaluationOutcome.fail("borncountrycode should be a 2-letter country code", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}