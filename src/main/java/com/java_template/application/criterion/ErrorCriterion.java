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
        // This is a predefined chain. Business logic is implemented in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(TransformJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<TransformJob> context) {
         TransformJob job = context.entity();
         if (job == null) {
             logger.warn("TransformJob entity is null in ErrorCriterion");
             return EvaluationOutcome.fail("TransformJob entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate presence of status
         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Missing job status", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate priority if present
         Integer priority = job.getPriority();
         if (priority != null && priority < 0) {
             return EvaluationOutcome.fail("Priority must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // ruleNames must be present (can be empty list as per entity contract)
         if (job.getRuleNames() == null) {
             return EvaluationOutcome.fail("ruleNames must be present (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate jobType allowed values
         String jobType = job.getJobType();
         if (jobType == null || jobType.isBlank()) {
             return EvaluationOutcome.fail("Missing jobType", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         boolean allowedJobType = "search_transform".equals(jobType) || "bulk_transform".equals(jobType);
         if (!allowedJobType) {
             return EvaluationOutcome.fail("Unsupported jobType: " + jobType, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If search_transform then searchFilterId is required
         if ("search_transform".equals(jobType)) {
             String sfId = job.getSearchFilterId();
             if (sfId == null || sfId.isBlank()) {
                 return EvaluationOutcome.fail("search_transform requires searchFilterId", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Special checks for FAILED status
         if ("FAILED".equalsIgnoreCase(status)) {
             if (job.getErrorMessage() == null || job.getErrorMessage().isBlank()) {
                 return EvaluationOutcome.fail("Job marked FAILED but errorMessage is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getCompletedAt() == null || job.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Job marked FAILED but completedAt is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // When failed, it's appropriate to report as a business-level failure outcome
             return EvaluationOutcome.fail("TransformJob has failed: " + job.getErrorMessage(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Special checks for COMPLETED status
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (job.getOutputLocation() == null || job.getOutputLocation().isBlank()) {
                 return EvaluationOutcome.fail("Job marked COMPLETED but outputLocation is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getCompletedAt() == null || job.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Job marked COMPLETED but completedAt is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             Integer rc = job.getResultCount();
             if (rc != null && rc < 0) {
                 return EvaluationOutcome.fail("resultCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If job is RUNNING or QUEUED or PENDING we only ensure basic consistency
         if ("RUNNING".equalsIgnoreCase(status) || "QUEUED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
             // startedAt should be present for RUNNING
             if ("RUNNING".equalsIgnoreCase(status)) {
                 if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
                     return EvaluationOutcome.fail("Job is RUNNING but startedAt is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}