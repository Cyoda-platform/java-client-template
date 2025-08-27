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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             logger.warn("Laureate entity is null");
             return EvaluationOutcome.fail("Laureate entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifiers and fields
         if (entity.getLaureateId() == null || entity.getLaureateId().isBlank()) {
             return EvaluationOutcome.fail("laureateId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAwardYear() == null || entity.getAwardYear().isBlank()) {
             return EvaluationOutcome.fail("awardYear is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getProcessingStatus() == null || entity.getProcessingStatus().isBlank()) {
             return EvaluationOutcome.fail("processingStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Provenance checks
         Laureate.Provenance prov = entity.getProvenance();
         if (prov == null) {
             return EvaluationOutcome.fail("provenance is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (prov.getIngestionJobId() == null || prov.getIngestionJobId().isBlank()) {
             return EvaluationOutcome.fail("provenance.ingestionJobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (prov.getSourceRecordId() == null || prov.getSourceRecordId().isBlank()) {
             return EvaluationOutcome.fail("provenance.sourceRecordId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (prov.getSourceTimestamp() == null || prov.getSourceTimestamp().isBlank()) {
             return EvaluationOutcome.fail("provenance.sourceTimestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks
         Integer age = entity.getAgeAtAward();
         if (age != null && age < 0) {
             return EvaluationOutcome.fail("ageAtAward must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validation errors must be empty for a successful validation outcome
         if (entity.getValidationErrors() != null && !entity.getValidationErrors().isEmpty()) {
             return EvaluationOutcome.fail("validationErrors present: " + entity.getValidationErrors().toString(), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: processingStatus must indicate a successful validation
         if (!"VALIDATED".equalsIgnoreCase(entity.getProcessingStatus())) {
             String msg = "processingStatus must be 'VALIDATED' for ValidationSuccessCriterion, current='" + entity.getProcessingStatus() + "'";
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}