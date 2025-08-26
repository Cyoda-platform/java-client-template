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

import java.util.List;
import java.util.Set;

@Component
public class SuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
         TransformJob job = context.entity();

         if (job == null) {
             return EvaluationOutcome.fail("TransformJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields
         if (job.getId() == null || job.getId().isBlank()) {
             return EvaluationOutcome.fail("Job id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getCreatedBy() == null || job.getCreatedBy().isBlank()) {
             return EvaluationOutcome.fail("createdBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getJobType() == null || job.getJobType().isBlank()) {
             return EvaluationOutcome.fail("jobType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSearchFilterId() == null || job.getSearchFilterId().isBlank()) {
             return EvaluationOutcome.fail("searchFilterId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getRuleNames() == null) {
             return EvaluationOutcome.fail("ruleNames must be present (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules
         Set<String> allowedJobTypes = Set.of("search_transform", "bulk_transform");
         if (!allowedJobTypes.contains(job.getJobType())) {
             return EvaluationOutcome.fail("Unsupported jobType: " + job.getJobType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         Set<String> allowedStatuses = Set.of("PENDING", "QUEUED", "RUNNING", "TRANSFORMING", "COMPLETED", "FAILED");
         String status = job.getStatus() == null ? "" : job.getStatus().toUpperCase();
         if (!allowedStatuses.contains(status)) {
             return EvaluationOutcome.fail("Unsupported status: " + job.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Priority business rule
         if (job.getPriority() != null && job.getPriority() < 0) {
             return EvaluationOutcome.fail("priority must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Status-specific quality checks
         if ("COMPLETED".equalsIgnoreCase(job.getStatus())) {
             if (job.getOutputLocation() == null || job.getOutputLocation().isBlank()) {
                 return EvaluationOutcome.fail("Completed job must have outputLocation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getCompletedAt() == null || job.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Completed job must have completedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getResultCount() == null || job.getResultCount() < 0) {
                 return EvaluationOutcome.fail("Completed job must have non-negative resultCount", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         if ("FAILED".equalsIgnoreCase(job.getStatus())) {
             if (job.getErrorMessage() == null || job.getErrorMessage().isBlank()) {
                 return EvaluationOutcome.fail("Failed job must include an errorMessage", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getCompletedAt() == null || job.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Failed job must have completedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         if ("RUNNING".equalsIgnoreCase(job.getStatus()) || "TRANSFORMING".equalsIgnoreCase(job.getStatus())) {
             if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
                 return EvaluationOutcome.fail("Running job should have startedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // ruleNames elements should not be blank
         List<String> rules = job.getRuleNames();
         for (String r : rules) {
             if (r == null || r.isBlank()) {
                 return EvaluationOutcome.fail("ruleNames must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If all checks pass
         return EvaluationOutcome.success();
    }
}