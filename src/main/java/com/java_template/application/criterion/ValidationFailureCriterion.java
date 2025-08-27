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
import java.time.format.DateTimeParseException;

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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Required fields (mirror entity.isValid() contract)
         if (entity.getId() == null) {
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

         // Year format: should be a 4-digit numeric year
         String yearStr = entity.getYear();
         int awardYear;
         try {
             awardYear = Integer.parseInt(yearStr);
             if (yearStr.length() != 4) {
                 return EvaluationOutcome.fail("year must be a 4-digit year", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("year must be a numeric year", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // born and died ISO date checks
         LocalDate bornDate = null;
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
             try {
                 bornDate = LocalDate.parse(entity.getBorn());
             } catch (DateTimeParseException dpe) {
                 return EvaluationOutcome.fail("born must be an ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         if (entity.getDied() != null && !entity.getDied().isBlank()) {
             try {
                 LocalDate diedDate = LocalDate.parse(entity.getDied());
                 if (bornDate != null && diedDate.isBefore(bornDate)) {
                     return EvaluationOutcome.fail("died date is before born date", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (DateTimeParseException dpe) {
                 return EvaluationOutcome.fail("died must be an ISO date (yyyy-MM-dd) or null", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If born and year present, check award year consistency
         if (bornDate != null) {
             try {
                 int derivedAge = awardYear - bornDate.getYear();
                 Integer reportedAge = entity.getDerivedAgeAtAward();
                 if (reportedAge != null && reportedAge != derivedAge) {
                     return EvaluationOutcome.fail("derived_age_at_award is inconsistent with born and year", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (awardYear < bornDate.getYear()) {
                     return EvaluationOutcome.fail("award year is earlier than born year", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (DateTimeException dte) {
                 // defensive, though parsing above should have caught issues
                 return EvaluationOutcome.fail("error evaluating born/year consistency", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // bornCountryCode normalization check (if present should be two letters)
         if (entity.getBornCountryCode() != null && !entity.getBornCountryCode().isBlank()) {
             String code = entity.getBornCountryCode();
             if (!code.matches("^[A-Za-z]{2}$")) {
                 return EvaluationOutcome.fail("bornCountryCode must be a 2-letter country code", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // ingestJobId, if present, must not be blank (entity.isValid() enforces, but double-check)
         if (entity.getIngestJobId() != null && entity.getIngestJobId().isBlank()) {
             return EvaluationOutcome.fail("ingestJobId, if present, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}