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
             return EvaluationOutcome.fail("Laureate entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields (per entity.isValid rules / functional requirements)
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
         if (entity.getPersistedAt() == null || entity.getPersistedAt().isBlank()) {
             return EvaluationOutcome.fail("persistedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRecordStatus() == null || entity.getRecordStatus().isBlank()) {
             return EvaluationOutcome.fail("recordStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Year format (expect YYYY)
         String year = entity.getYear();
         if (!year.matches("\\d{4}")) {
             return EvaluationOutcome.fail("year must be a 4-digit year (YYYY)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // recordStatus business rule: must be one of NEW, UPDATED, UNCHANGED
         String status = entity.getRecordStatus();
         if (!( "NEW".equalsIgnoreCase(status) || "UPDATED".equalsIgnoreCase(status) || "UNCHANGED".equalsIgnoreCase(status) )) {
             return EvaluationOutcome.fail("recordStatus must be one of NEW, UPDATED or UNCHANGED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // bornCountryCode if present should be 2-letter code (basic check)
         String bornCountryCode = entity.getBornCountryCode();
         if (bornCountryCode != null && !bornCountryCode.isBlank() && bornCountryCode.length() != 2) {
             return EvaluationOutcome.fail("bornCountryCode must be a 2-letter country code when provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // derivedAgeAtAward consistency check when born and derivedAgeAtAward are present
         Integer derivedAge = entity.getDerivedAgeAtAward();
         String born = entity.getBorn();
         if (derivedAge != null) {
             if (derivedAge < 0) {
                 return EvaluationOutcome.fail("derivedAgeAtAward must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (born != null && born.length() >= 4) {
                 try {
                     int birthYear = Integer.parseInt(born.substring(0,4));
                     int awardYear = Integer.parseInt(year);
                     int computed = awardYear - birthYear;
                     if (computed < 0) {
                         return EvaluationOutcome.fail("computed derived ageAtAward is negative (born year after award year)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                     }
                     if (!derivedAge.equals(computed)) {
                         return EvaluationOutcome.fail("derivedAgeAtAward does not match born/year (expected " + computed + ")", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                     }
                 } catch (NumberFormatException nfe) {
                     // If parsing fails, treat as data quality failure
                     return EvaluationOutcome.fail("born or year not parseable as year for derived age validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Basic gender normalization check (if present, must not be blank)
         String gender = entity.getGender();
         if (gender != null && gender.isBlank()) {
             return EvaluationOutcome.fail("gender, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}