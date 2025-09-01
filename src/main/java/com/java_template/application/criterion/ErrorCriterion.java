package com.java_template.application.criterion;

import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
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
public class ErrorCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ErrorCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeeklySendJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return modelSpec.operationName() != null && className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklySendJob> context) {
         WeeklySendJob job = context.entity();

         // Basic validation: status must be present
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("WeeklySendJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus().trim();

         // Explicit failure state detected
         if ("FAILED".equalsIgnoreCase(status)) {
             String err = job.getErrorMessage();
             if (err == null || err.isBlank()) {
                 return EvaluationOutcome.fail("WeeklySendJob is FAILED but no errorMessage provided", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             return EvaluationOutcome.fail("WeeklySendJob failed: " + err, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Running state with an attached error indicates a problem
         if ("RUNNING".equalsIgnoreCase(status)) {
             String err = job.getErrorMessage();
             if (err != null && !err.isBlank()) {
                 return EvaluationOutcome.fail("WeeklySendJob RUNNING with error: " + err, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (job.getRunAt() == null || job.getRunAt().isBlank()) {
                 return EvaluationOutcome.fail("WeeklySendJob RUNNING but missing runAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Jobs that report dispatch/completion should reference a CatFact
         if ("DISPATCHED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
             if (job.getCatFactTechnicalId() == null || job.getCatFactTechnicalId().isBlank()) {
                 return EvaluationOutcome.fail("WeeklySendJob status is " + status + " but catFactTechnicalId is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // No error conditions detected
         return EvaluationOutcome.success();
    }
}