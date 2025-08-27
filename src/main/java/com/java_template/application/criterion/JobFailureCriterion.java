package com.java_template.application.criterion;

import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
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

import java.time.Duration;
import java.time.Instant;

@Component
public class JobFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeeklyJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklyJob> context) {
         WeeklyJob job = context.entity();
         if (job == null) {
             logger.warn("WeeklyJob entity is null in JobFailureCriterion");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             logger.warn("WeeklyJob {} has missing status", job.getName());
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Direct failure reported by the job
         if ("FAILED".equalsIgnoreCase(status)) {
             String policy = job.getFailurePolicy();
             String msg = "Job reported status FAILED";
             if (policy != null && !policy.isBlank()) {
                 msg += " (failurePolicy: " + policy + ")";
             }
             logger.info("JobFailureCriterion detected FAILED status for job {}", job.getName());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Detect potentially stuck or misconfigured running jobs
         if ("RUNNING".equalsIgnoreCase(status)) {
             // apiEndpoint must be present to perform ingestion
             if (job.getApiEndpoint() == null || job.getApiEndpoint().isBlank()) {
                 logger.info("Job {} is RUNNING but apiEndpoint is missing", job.getName());
                 return EvaluationOutcome.fail("Job is RUNNING but apiEndpoint is missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }

             // lastRunAt must be a valid ISO instant; if it's too old the job may be stuck
             String lastRunAt = job.getLastRunAt();
             if (lastRunAt == null || lastRunAt.isBlank()) {
                 logger.info("Job {} is RUNNING but lastRunAt is missing", job.getName());
                 return EvaluationOutcome.fail("Job is RUNNING but lastRunAt is missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             try {
                 Instant last = Instant.parse(lastRunAt);
                 Instant cutoff = Instant.now().minus(Duration.ofHours(24));
                 if (last.isBefore(cutoff)) {
                     logger.info("Job {} appears stuck: lastRunAt {} is older than 24 hours", job.getName(), lastRunAt);
                     return EvaluationOutcome.fail("Job appears stuck: lastRunAt older than 24 hours while RUNNING", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             } catch (Exception ex) {
                 logger.warn("Invalid lastRunAt for job {}: {}", job.getName(), ex.getMessage());
                 return EvaluationOutcome.fail("lastRunAt timestamp invalid: " + ex.getMessage(), StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // For other statuses (PENDING, COMPLETED, etc.) there is no failure condition here
         return EvaluationOutcome.success();
    }
}