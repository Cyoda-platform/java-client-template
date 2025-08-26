package com.java_template.application.criterion;

import com.java_template.application.entity.transformjob.version_1.TransformJob;
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
public class ErrorCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ErrorCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(TransformJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<TransformJob> context) {
         TransformJob entity = context.entity();

         if (entity == null) {
             logger.debug("TransformJob entity is null in ErrorCriterion");
             return EvaluationOutcome.fail("TransformJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // When job failed, errorMessage and completedAt must be present
         if ("FAILED".equalsIgnoreCase(status)) {
             if (entity.getErrorMessage() == null || entity.getErrorMessage().isBlank()) {
                 return EvaluationOutcome.fail("Failed job must include errorMessage", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Failed job must include completedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // When job completed, outputLocation and completedAt must be present and resultCount must be non-negative
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (entity.getOutputLocation() == null || entity.getOutputLocation().isBlank()) {
                 return EvaluationOutcome.fail("Completed job must include outputLocation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Completed job must include completedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (entity.getResultCount() == null) {
                 return EvaluationOutcome.fail("Completed job must include resultCount", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (entity.getResultCount() < 0) {
                 return EvaluationOutcome.fail("resultCount must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // When job is running, startedAt should be present
         if ("RUNNING".equalsIgnoreCase(status)) {
             if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
                 return EvaluationOutcome.fail("Running job should have startedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // For queued or pending jobs, ensure basic required references exist
         if ("QUEUED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status) || "CREATED".equalsIgnoreCase(status)) {
             if (entity.getSearchFilterId() == null || entity.getSearchFilterId().isBlank()) {
                 return EvaluationOutcome.fail("Job must reference a searchFilterId", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getRuleNames() == null) {
                 return EvaluationOutcome.fail("ruleNames list must be present (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // Unknown status values are considered validation failures
         return EvaluationOutcome.fail("Unknown status value: " + status, StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}