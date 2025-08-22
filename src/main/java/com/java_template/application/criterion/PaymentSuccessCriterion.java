package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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
public class PaymentSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionJob> context) {
         AdoptionJob job = context.entity();

         if (job == null) {
             logger.warn("AdoptionJob entity is null in PaymentSuccessCriterion");
             return EvaluationOutcome.fail("AdoptionJob is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Double fee = job.getFee();
         // If no fee or zero, no payment required -> success
         if (fee == null || fee.doubleValue() <= 0.0) {
             return EvaluationOutcome.success();
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Job status is required for payment validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String processedAt = job.getProcessedAt();
         String normalizedStatus = status.trim().toLowerCase();

         // Business rules:
         // - For completed jobs with a fee, processedAt must be present
         // - For jobs in post_processing, payment should typically be completed; if not present mark as business rule failure
         // - For failed jobs, report business rule failure indicating payment did not succeed
         switch (normalizedStatus) {
             case "completed":
                 if (processedAt == null || processedAt.isBlank()) {
                     return EvaluationOutcome.fail("Payment must be processed for completed adoption jobs", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 return EvaluationOutcome.success();
             case "post_processing":
                 if (processedAt == null || processedAt.isBlank()) {
                     return EvaluationOutcome.fail("Payment pending for job in post_processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 return EvaluationOutcome.success();
             case "failed":
                 return EvaluationOutcome.fail("Adoption job failed; payment not completed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             default:
                 // For other statuses (pending, validation, review, approved, etc.) payment may be pending and that's acceptable
                 return EvaluationOutcome.success();
         }
    }
}