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
public class SendFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public SendFailedCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job entity = context.entity();
         if (entity == null) {
             logger.warn("SendFailedCriterion invoked with null Job entity");
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Only applicable to NOTIFY jobs
         String type = entity.getType();
         if (type == null || type.isBlank()) {
             return EvaluationOutcome.fail("Job type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"NOTIFY".equalsIgnoreCase(type)) {
             // Not a notify job — criterion not applicable, consider as success
             return EvaluationOutcome.success();
         }

         // For notify jobs, we expect status to be present
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Job status is required for NOTIFY jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Only evaluate failed sends
         if (!"FAILED".equalsIgnoreCase(status)) {
             // Not failed -> nothing to do
             return EvaluationOutcome.success();
         }

         Integer attempts = entity.getAttemptCount();
         if (attempts == null) {
             return EvaluationOutcome.fail("attemptCount is missing for failed NOTIFY job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (attempts < 0) {
             return EvaluationOutcome.fail("attemptCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: if attempts >= MAX_RETRY_ATTEMPTS escalate (permanent failure)
         if (attempts >= MAX_RETRY_ATTEMPTS) {
             String msg = String.format("NOTIFY job exceeded max retry attempts (%d)", MAX_RETRY_ATTEMPTS);
             logger.info(msg + " - job eligible for escalation");
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Otherwise the send failed but can be retried
         String msg = String.format("NOTIFY send failed (attempts=%d) - eligible for retry", attempts);
         logger.info(msg);
         return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}