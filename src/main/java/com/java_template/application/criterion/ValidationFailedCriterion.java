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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("ValidationFailedCriterion: received null Job entity");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields validation (use only existing getters)
         if (job.getName() == null || job.getName().isBlank()) {
             return EvaluationOutcome.fail("Job.name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getScheduleType() == null || job.getScheduleType().isBlank()) {
             return EvaluationOutcome.fail("Job.scheduleType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getScheduleSpec() == null || job.getScheduleSpec().isBlank()) {
             return EvaluationOutcome.fail("Job.scheduleSpec is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
             return EvaluationOutcome.fail("Job.sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getEnabled() == null) {
             return EvaluationOutcome.fail("Job.enabled flag must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule checks
         String scheduleType = job.getScheduleType().trim();
         // Allowed scheduleType values based on functional requirements: "one-time" or "recurring"
         if (!(scheduleType.equalsIgnoreCase("one-time") || scheduleType.equalsIgnoreCase("recurring"))) {
             return EvaluationOutcome.fail("Job.scheduleType must be either 'one-time' or 'recurring'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If recurring, do a simple cron-like heuristic validation: expect at least 5 space-separated fields (basic sanity check)
         if (scheduleType.equalsIgnoreCase("recurring")) {
             String spec = job.getScheduleSpec().trim();
             String[] tokens = spec.split("\\s+");
             if (tokens.length < 5) {
                 return EvaluationOutcome.fail("Job.scheduleSpec does not look like a valid cron expression for recurring schedules", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If one-time, ensure scheduleSpec is non-blank (already checked) and not looking like a cron (basic heuristic)
         if (scheduleType.equalsIgnoreCase("one-time")) {
             String spec = job.getScheduleSpec().trim();
             // simple heuristic: if it contains 5 or more space-separated tokens it's likely a cron and thus invalid for one-time
             String[] tokens = spec.split("\\s+");
             if (tokens.length >= 5) {
                 return EvaluationOutcome.fail("Job.scheduleSpec appears to be a recurring schedule but scheduleType is 'one-time'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Additional data-quality check: lastResultSummary length (if present) shouldn't be excessively large
         if (job.getLastResultSummary() != null && job.getLastResultSummary().length() > 10_000) {
             return EvaluationOutcome.fail("Job.lastResultSummary is unusually large", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}