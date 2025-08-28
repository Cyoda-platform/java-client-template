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

import java.time.DateTimeException;
import java.time.LocalDate;

@Component
public class IsValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidCriterion(SerializerFactory serializerFactory) {
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

         if (entity == null) {
             return EvaluationOutcome.fail("Laureate entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // id must be present
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields
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

         // year should be a valid integer (award year)
         try {
             Integer.parseInt(entity.getYear());
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("year must be a valid integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // ageAtAward if present must be non-negative
         if (entity.getAgeAtAward() != null && entity.getAgeAtAward() < 0) {
             return EvaluationOutcome.fail("ageAtAward must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // born and died if present should be valid ISO local dates (yyyy-MM-dd)
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
             try {
                 LocalDate.parse(entity.getBorn());
             } catch (DateTimeException dte) {
                 return EvaluationOutcome.fail("born must be a valid ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }
         if (entity.getDied() != null && !entity.getDied().isBlank()) {
             try {
                 LocalDate.parse(entity.getDied());
             } catch (DateTimeException dte) {
                 return EvaluationOutcome.fail("died must be a valid ISO date (yyyy-MM-dd) or null", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Basic sanity: if died date is present, ensure born <= died
         if (entity.getBorn() != null && !entity.getBorn().isBlank()
                 && entity.getDied() != null && !entity.getDied().isBlank()) {
             try {
                 LocalDate bornDate = LocalDate.parse(entity.getBorn());
                 LocalDate diedDate = LocalDate.parse(entity.getDied());
                 if (diedDate.isBefore(bornDate)) {
                     return EvaluationOutcome.fail("died date cannot be before born date", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (DateTimeException ignored) {
                 // parsing errors handled above; ignore here
             }
         }

         // All validations passed
         return EvaluationOutcome.success();
    }
}