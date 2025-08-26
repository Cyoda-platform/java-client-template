package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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
public class JobExecutionExceptionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final int MAX_ATTEMPTS = 3;

    public JobExecutionExceptionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             logger.warn("Job entity is null in context");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             logger.debug("Job {}: missing status", job);
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic sanity: job type must be present
         String type = job.getType();
         if (type == null || type.isBlank()) {
             logger.debug("Job {}: missing type", job);
             return EvaluationOutcome.fail("Job type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Only evaluate execution exceptions for FAILED jobs
         if (!"FAILED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.success();
         }

         Integer attempts = job.getAttemptCount();
         if (attempts == null) {
             logger.debug("Job with FAILED status but missing attemptCount: {}", job);
             return EvaluationOutcome.fail("Attempt count is required for failed jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (attempts < 0) {
             logger.debug("Job has negative attemptCount: {}", attempts);
             return EvaluationOutcome.fail("Attempt count must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business decision: if attempts below threshold -> eligible for automatic retry,
         // otherwise escalate for manual intervention.
         if (attempts < MAX_ATTEMPTS) {
             logger.info("Job failed but eligible for retry (attempts={} < max={}) for job type={}", attempts, MAX_ATTEMPTS, type);
             return EvaluationOutcome.fail(
                 "Job failed and is eligible for retry",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         } else {
             logger.info("Job failed and reached max attempts (attempts={} >= max={}) - escalate to ADMIN for job type={}", attempts, MAX_ATTEMPTS, type);
             return EvaluationOutcome.fail(
                 "Job failed and reached max retry attempts; escalate to ADMIN",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }
    }
}