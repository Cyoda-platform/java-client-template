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

import java.time.DateTimeException;
import java.time.Instant;

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
             return EvaluationOutcome.fail("TransformJob entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String jobType = entity.getJobType();
         if (jobType == null || jobType.isBlank()) {
             return EvaluationOutcome.fail("jobType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         } else {
             // Business allowed job types based on functional requirements
             if (!"search_transform".equalsIgnoreCase(jobType) && !"bulk_transform".equalsIgnoreCase(jobType)) {
                 return EvaluationOutcome.fail("unsupported jobType: " + jobType, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // For search_transform jobs, searchFilterId must be present
         if ("search_transform".equalsIgnoreCase(jobType)) {
             if (entity.getSearchFilterId() == null || entity.getSearchFilterId().isBlank()) {
                 return EvaluationOutcome.fail("search_transform requires searchFilterId", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Common checks by status
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (entity.getOutputLocation() == null || entity.getOutputLocation().isBlank()) {
                 return EvaluationOutcome.fail("Completed job missing outputLocation", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Completed job missing completedAt", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getResultCount() == null) {
                 return EvaluationOutcome.fail("Completed job missing resultCount", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getResultCount() < 0) {
                 return EvaluationOutcome.fail("resultCount must not be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getResultCount() == 0) {
                 return EvaluationOutcome.fail("Completed job has zero results", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // If both timestamps present, ensure chronological order
             if (entity.getStartedAt() != null && !entity.getStartedAt().isBlank()) {
                 try {
                     Instant started = Instant.parse(entity.getStartedAt());
                     Instant completed = Instant.parse(entity.getCompletedAt());
                     if (started.isAfter(completed)) {
                         return EvaluationOutcome.fail("startedAt is after completedAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                     }
                 } catch (DateTimeException dte) {
                     logger.debug("Timestamp parse error for TransformJob {}: {}", entity.getId(), dte.getMessage());
                     return EvaluationOutcome.fail("Invalid timestamp format for startedAt or completedAt", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             }
         } else if ("FAILED".equalsIgnoreCase(status)) {
             if (entity.getErrorMessage() == null || entity.getErrorMessage().isBlank()) {
                 return EvaluationOutcome.fail("Failed job missing errorMessage", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Failed job missing completedAt", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } else if ("RUNNING".equalsIgnoreCase(status)) {
             if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
                 return EvaluationOutcome.fail("Running job missing startedAt", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } else if ("QUEUED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
             // No strict additional checks, but ensure priority if present is non-negative
             if (entity.getPriority() != null && entity.getPriority() < 0) {
                 return EvaluationOutcome.fail("priority must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } else {
             // Unknown status may indicate business rule issue
             return EvaluationOutcome.fail("Unknown job status: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // ruleNames must be present (can be empty list) — TransformJob.isValid ensures non-null, but double-check
         if (entity.getRuleNames() == null) {
             return EvaluationOutcome.fail("ruleNames must be present (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}