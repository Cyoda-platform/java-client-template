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

         // Business rule: If status is RUNNING or VALIDATING then lastRunAt should be present
         String status = job.getStatus();
         if (status != null && (status.equalsIgnoreCase("RUNNING") || status.equalsIgnoreCase("VALIDATING"))) {
             if (job.getLastRunAt() == null || job.getLastRunAt().isBlank()) {
                 return EvaluationOutcome.fail("lastRunAt must be set when status is RUNNING or VALIDATING", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Optional checks: resultSummary if present must not be blank
         if (job.getResultSummary() != null && job.getResultSummary().isBlank()) {
             return EvaluationOutcome.fail("resultSummary, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}