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
public class JobRetryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Align with system retry policy (engine used 3 as demo)
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public JobRetryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             logger.debug("Job entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Only FAILED jobs are eligible for retry
         if (!"FAILED".equalsIgnoreCase(job.getStatus())) {
             return EvaluationOutcome.fail("Job is not in FAILED state, not eligible for retry",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // attemptCount must be present and non-negative
         if (job.getAttemptCount() == null) {
             return EvaluationOutcome.fail("attemptCount is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (job.getAttemptCount() < 0) {
             return EvaluationOutcome.fail("attemptCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If max retries reached, do not retry automatically
         if (job.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
             return EvaluationOutcome.fail(
                     "Maximum retry attempts reached (" + MAX_RETRY_ATTEMPTS + "), escalate to manual review",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Basic payload sanity checks required for retrying (payload must be present and minimally valid)
         Job.Payload payload = job.getPayload();
         if (payload == null) {
             return EvaluationOutcome.fail("Job payload is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (payload.getApiUrl() == null || payload.getApiUrl().isBlank()) {
             return EvaluationOutcome.fail("payload.apiUrl is required to retry job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (payload.getRows() == null || payload.getRows() < 0) {
             return EvaluationOutcome.fail("payload.rows must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed — job is eligible for automatic retry
         return EvaluationOutcome.success();
    }
}