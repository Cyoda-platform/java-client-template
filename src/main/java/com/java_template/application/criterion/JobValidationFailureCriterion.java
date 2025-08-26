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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // createdAt must be present
         if (job.getCreatedAt() == null || job.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // type must be present
         if (job.getType() == null || job.getType().isBlank()) {
             return EvaluationOutcome.fail("type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // status must be present
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // attemptCount must be non-null and non-negative
         if (job.getAttemptCount() == null) {
             return EvaluationOutcome.fail("attemptCount must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getAttemptCount() < 0) {
             return EvaluationOutcome.fail("attemptCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // payload presence and basic payload validation
         Job.Payload payload = job.getPayload();
         if (payload == null) {
             return EvaluationOutcome.fail("payload is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payload.getApiUrl() == null || payload.getApiUrl().isBlank()) {
             return EvaluationOutcome.fail("payload.apiUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payload.getRows() == null) {
             return EvaluationOutcome.fail("payload.rows must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payload.getRows() < 0) {
             return EvaluationOutcome.fail("payload.rows must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: type must be one of the supported orchestration job types
         Set<String> allowedTypes = Set.of("INGEST", "TRANSFORM", "NOTIFY");
         if (!allowedTypes.contains(job.getType())) {
             return EvaluationOutcome.fail("Unsupported job type: " + job.getType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Business rule: status must be one of the recognized lifecycle states
         Set<String> allowedStatuses = Set.of("PENDING", "RUNNING", "FAILED", "COMPLETED");
         if (!allowedStatuses.contains(job.getStatus())) {
             return EvaluationOutcome.fail("Unsupported job status: " + job.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}