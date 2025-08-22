package com.java_template.application.criterion;

import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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
public class FetchSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob entity = context.entity();

         // Basic required fields validation
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSource() == null || entity.getSource().isBlank()) {
             return EvaluationOutcome.fail("source is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStartTime() == null || entity.getStartTime().isBlank()) {
             return EvaluationOutcome.fail("start_time is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getConfig() == null) {
             return EvaluationOutcome.fail("config is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: a successful fetch should move the job to 'parsing' status (per workflow)
         if (!"parsing".equalsIgnoreCase(entity.getStatus())) {
             // If job explicitly failed, surface as business rule failure with any error message
             if ("failed".equalsIgnoreCase(entity.getStatus())) {
                 String err = entity.getErrorMessage() == null ? "fetch failed" : ("fetch failed: " + entity.getErrorMessage());
                 return EvaluationOutcome.fail(err, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             return EvaluationOutcome.fail("fetch not completed; expected status 'parsing' but was '" + entity.getStatus() + "'",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality checks for fetched count and error message
         if (entity.getFetchedCount() == null) {
             return EvaluationOutcome.fail("fetched_count is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getFetchedCount() < 0) {
             return EvaluationOutcome.fail("fetched_count must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getErrorMessage() != null && !entity.getErrorMessage().isBlank()) {
             return EvaluationOutcome.fail("error_message present despite parsing status: " + entity.getErrorMessage(),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}