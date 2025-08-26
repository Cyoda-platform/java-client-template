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

import java.util.Set;

@Component
public class JobValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // align with retry policy used elsewhere in prototype (max 3 attempts)
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Set<String> ALLOWED_TYPES = Set.of("INGEST", "TRANSFORM", "NOTIFY");
    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "VALIDATING", "RUNNING", "FAILED", "COMPLETED", "RETRY");

    public JobValidationCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job entity = context.entity();

         if (entity == null) {
             logger.debug("Job entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getType() == null || entity.getType().isBlank()) {
             return EvaluationOutcome.fail("type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAttemptCount() == null) {
             return EvaluationOutcome.fail("attemptCount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAttemptCount() < 0) {
             return EvaluationOutcome.fail("attemptCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: allowed job types
         String type = entity.getType().trim();
         if (!ALLOWED_TYPES.contains(type.toUpperCase())) {
             return EvaluationOutcome.fail("Unsupported job type: " + entity.getType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Business rule: allowed statuses
         String status = entity.getStatus().trim();
         if (!ALLOWED_STATUSES.contains(status.toUpperCase())) {
             return EvaluationOutcome.fail("Unknown job status: " + entity.getStatus(), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Payload validation (Payload class exists on Job)
         Job.Payload payload = entity.getPayload();
         if (payload == null) {
             return EvaluationOutcome.fail("payload is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payload.getApiUrl() == null || payload.getApiUrl().isBlank()) {
             return EvaluationOutcome.fail("payload.apiUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payload.getRows() == null) {
             return EvaluationOutcome.fail("payload.rows is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payload.getRows() < 0) {
             return EvaluationOutcome.fail("payload.rows must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: attempts limit (retry/escalation policy)
         if (entity.getAttemptCount() > MAX_RETRY_ATTEMPTS) {
             return EvaluationOutcome.fail("attemptCount exceeds allowed maximum (" + MAX_RETRY_ATTEMPTS + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality consistency checks
         if ("COMPLETED".equalsIgnoreCase(status) && (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank())) {
             return EvaluationOutcome.fail("completedAt must be set when status is COMPLETED", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if ("RUNNING".equalsIgnoreCase(status) && (entity.getStartedAt() == null || entity.getStartedAt().isBlank())) {
             return EvaluationOutcome.fail("startedAt must be set when status is RUNNING", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if ("PENDING".equalsIgnoreCase(status) && entity.getStartedAt() != null && !entity.getStartedAt().isBlank()) {
             return EvaluationOutcome.fail("startedAt should be null when status is PENDING", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}