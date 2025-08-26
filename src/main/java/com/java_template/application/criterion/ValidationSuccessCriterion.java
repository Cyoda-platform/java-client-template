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

import java.time.Year;

@Component
public class ValidationSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name (case-sensitive)
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             logger.debug("ValidationSuccessCriterion: received null entity");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields: id, fullName, category, year, status, createdAt
         if (isBlank(entity.getId())) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getFullName())) {
             return EvaluationOutcome.fail("fullName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getCategory())) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getYear())) {
             return EvaluationOutcome.fail("year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getStatus())) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getCreatedAt())) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality check: year should be numeric and within reasonable bounds
         String yearStr = entity.getYear();
         if (yearStr != null && !yearStr.isBlank()) {
             if (!yearStr.matches("\\d{3,4}")) {
                 return EvaluationOutcome.fail("year is not a valid numeric year", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             try {
                 int y = Integer.parseInt(yearStr);
                 int current = Year.now().getValue();
                 if (y < 1000 || y > current + 1) { // allow next-year provisional records
                     return EvaluationOutcome.fail("year is out of expected range", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (NumberFormatException nfe) {
                 return EvaluationOutcome.fail("year is not a valid numeric year", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: entity must be in VALIDATED status to be considered validation success
         if (!"VALIDATED".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("entity status is not VALIDATED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Additional data-quality warning candidates (not failing):
         // - birthCountry or birthCity missing could be enriched later; do not fail but note via reason attachment if supported by serializer
         if (isBlank(entity.getBirthCountry()) || isBlank(entity.getBirthCity())) {
             // We cannot call serializer-specific warning APIs here (unknown), but the ReasonAttachmentStrategy.toWarnings()
             // used in the chain can attach warnings produced elsewhere. Keep validation successful.
             logger.debug("ValidationSuccessCriterion: birthCountry or birthCity missing for entity id={}", entity.getId());
         }

         return EvaluationOutcome.success();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}