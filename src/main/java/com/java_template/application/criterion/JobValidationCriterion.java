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

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class JobValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobValidationCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             logger.warn("JobValidationCriterion: entity is null");
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields
         if (job.getJobId() == null || job.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getName() == null || job.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate sourceEndpoint is a well-formed HTTP/HTTPS URI
         String source = job.getSourceEndpoint();
         try {
             URI uri = URI.create(source);
             String scheme = uri.getScheme();
             if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                 return EvaluationOutcome.fail("sourceEndpoint must be a valid http/https URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (IllegalArgumentException ex) {
             return EvaluationOutcome.fail("sourceEndpoint must be a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Parameters map must be present (can be empty)
         if (job.getParameters() == null) {
             return EvaluationOutcome.fail("parameters map must be present (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // retryCount must be non-null and non-negative
         if (job.getRetryCount() == null) {
             return EvaluationOutcome.fail("retryCount must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getRetryCount() < 0) {
             return EvaluationOutcome.fail("retryCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate timestamps if provided (createdAt and lastRunAt) are ISO-8601 parseable
         if (job.getCreatedAt() != null && !job.getCreatedAt().isBlank()) {
             try {
                 Instant.parse(job.getCreatedAt());
             } catch (DateTimeParseException e) {
                 return EvaluationOutcome.fail("createdAt must be ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }
         if (job.getLastRunAt() != null && !job.getLastRunAt().isBlank()) {
             try {
                 Instant.parse(job.getLastRunAt());
             } catch (DateTimeParseException e) {
                 return EvaluationOutcome.fail("lastRunAt must be ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: If status is RUNNING or VALIDATING then lastRunAt should be present
         String status = job.getStatus();
         if (status != null && (status.equalsIgnoreCase("RUNNING") || status.equalsIgnoreCase("VALIDATING"))) {
             if (job.getLastRunAt() == null || job.getLastRunAt().isBlank()) {
                 return EvaluationOutcome.fail("lastRunAt must be set when status is RUNNING or VALIDATING", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Business rule: If status is COMPLETED then resultSummary should be present and non-blank
         if (status != null && status.equalsIgnoreCase("COMPLETED")) {
             if (job.getResultSummary() == null || job.getResultSummary().isBlank()) {
                 return EvaluationOutcome.fail("resultSummary must be present when status is COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Optional checks: resultSummary if present must not be blank
         if (job.getResultSummary() != null && job.getResultSummary().isBlank()) {
             return EvaluationOutcome.fail("resultSummary, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // schedule (optional) - if provided must not be blank
         if (job.getSchedule() != null && job.getSchedule().isBlank()) {
             return EvaluationOutcome.fail("schedule, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}