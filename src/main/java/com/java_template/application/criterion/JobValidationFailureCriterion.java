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
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Component
public class JobValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobValidationFailureCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // createdAt must be present and parseable
         if (job.getCreatedAt() == null || job.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         Instant createdAtInstant;
         try {
             createdAtInstant = Instant.parse(job.getCreatedAt());
         } catch (DateTimeParseException ex) {
             logger.debug("createdAt parse error for job: {}", ex.getMessage());
             return EvaluationOutcome.fail("createdAt must be an ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // type must be present
         if (job.getType() == null || job.getType().isBlank()) {
             return EvaluationOutcome.fail("type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // status must be present
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // attemptCount must be non-null and non-negative (and reasonable)
         if (job.getAttemptCount() == null) {
             return EvaluationOutcome.fail("attemptCount must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getAttemptCount() < 0) {
             return EvaluationOutcome.fail("attemptCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getAttemptCount() > 100) {
             // extremely large attempt counts indicate data quality issues
             return EvaluationOutcome.fail("attemptCount is unreasonably large", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // payload presence and validation depending on job type
         Job.Payload payload = job.getPayload();
         String type = job.getType().trim().toUpperCase();

         // Business rule: type must be one of the supported orchestration job types
         Set<String> allowedTypes = Set.of("INGEST", "TRANSFORM", "NOTIFY");
         if (!allowedTypes.contains(type)) {
             return EvaluationOutcome.fail("Unsupported job type: " + job.getType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For INGEST jobs, payload.apiUrl and payload.rows are required and must be valid
         if ("INGEST".equals(type)) {
             if (payload == null) {
                 return EvaluationOutcome.fail("payload is required for INGEST jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (payload.getApiUrl() == null || payload.getApiUrl().isBlank()) {
                 return EvaluationOutcome.fail("payload.apiUrl is required for INGEST jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // validate apiUrl syntax and scheme
             try {
                 URI uri = new URI(payload.getApiUrl());
                 String scheme = uri.getScheme();
                 if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                     return EvaluationOutcome.fail("payload.apiUrl must be a valid http/https URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             } catch (URISyntaxException e) {
                 logger.debug("Invalid apiUrl for job: {}", e.getMessage());
                 return EvaluationOutcome.fail("payload.apiUrl must be a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (payload.getRows() == null) {
                 return EvaluationOutcome.fail("payload.rows must be provided for INGEST jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (payload.getRows() <= 0) {
                 return EvaluationOutcome.fail("payload.rows must be greater than zero for INGEST jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } else {
             // For TRANSFORM and NOTIFY jobs, payload is optional; if rows provided ensure non-negative
             if (payload != null && payload.getRows() != null && payload.getRows() < 0) {
                 return EvaluationOutcome.fail("payload.rows must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Business rule: status must be one of the recognized lifecycle states
         Set<String> allowedStatuses = Set.of("PENDING", "RUNNING", "FAILED", "COMPLETED");
         String status = job.getStatus().trim().toUpperCase();
         if (!allowedStatuses.contains(status)) {
             return EvaluationOutcome.fail("Unsupported job status: " + job.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate timestamps consistency when present
         Instant startedAtInstant = null;
         if (job.getStartedAt() != null && !job.getStartedAt().isBlank()) {
             try {
                 startedAtInstant = Instant.parse(job.getStartedAt());
             } catch (DateTimeParseException ex) {
                 logger.debug("startedAt parse error for job: {}", ex.getMessage());
                 return EvaluationOutcome.fail("startedAt must be an ISO-8601 timestamp when provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // startedAt must not be before createdAt
             if (startedAtInstant.isBefore(createdAtInstant)) {
                 return EvaluationOutcome.fail("startedAt cannot be before createdAt", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         Instant completedAtInstant = null;
         if (job.getCompletedAt() != null && !job.getCompletedAt().isBlank()) {
             try {
                 completedAtInstant = Instant.parse(job.getCompletedAt());
             } catch (DateTimeParseException ex) {
                 logger.debug("completedAt parse error for job: {}", ex.getMessage());
                 return EvaluationOutcome.fail("completedAt must be an ISO-8601 timestamp when provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // If startedAt present, completedAt must not be before startedAt
             if (startedAtInstant != null && completedAtInstant.isBefore(startedAtInstant)) {
                 return EvaluationOutcome.fail("completedAt cannot be before startedAt", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             // completedAt should not be before createdAt (defensive)
             if (completedAtInstant.isBefore(createdAtInstant)) {
                 return EvaluationOutcome.fail("completedAt cannot be before createdAt", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Enforce sensible relationships between lifecycle state and timestamps
         if ("RUNNING".equals(status) && startedAtInstant == null) {
             return EvaluationOutcome.fail("RUNNING jobs must have startedAt set", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if ("COMPLETED".equals(status) && completedAtInstant == null) {
             return EvaluationOutcome.fail("COMPLETED jobs must have completedAt set", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if ("PENDING".equals(status) && (startedAtInstant != null || completedAtInstant != null)) {
             return EvaluationOutcome.fail("PENDING jobs must not have startedAt or completedAt set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}