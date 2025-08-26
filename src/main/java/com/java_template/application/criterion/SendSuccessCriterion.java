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
public class SendSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SendSuccessCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic presence checks
         if (job.getType() == null || job.getType().isBlank()) {
             return EvaluationOutcome.fail("Job type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // This criterion is intended to evaluate send/notification jobs
         if (!"NOTIFY".equalsIgnoreCase(job.getType())) {
             return EvaluationOutcome.fail("SendSuccessCriterion applies only to NOTIFY jobs", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If job explicitly failed -> business rule failure
         if ("FAILED".equalsIgnoreCase(job.getStatus())) {
             return EvaluationOutcome.fail("Notification job has failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Completed state is required for a successful send
         if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
             return EvaluationOutcome.fail("Notification job is not completed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // attemptCount must be present and at least 1 when completed
         if (job.getAttemptCount() == null) {
             return EvaluationOutcome.fail("Attempt count missing for completed notification job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (job.getAttemptCount() < 1) {
             return EvaluationOutcome.fail("Completed notification job must have at least one attempt recorded", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // completedAt should be present for completed jobs
         if (job.getCompletedAt() == null || job.getCompletedAt().isBlank()) {
             return EvaluationOutcome.fail("completedAt timestamp missing for completed notification job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}