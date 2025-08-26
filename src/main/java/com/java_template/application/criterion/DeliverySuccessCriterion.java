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
public class DeliverySuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeliverySuccessCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklySendJob> context) {
         WeeklySendJob job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("WeeklySendJob is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Normalize for comparisons
         String normalizedStatus = status.trim();

         // Success condition: job completed and has required delivery information
         if ("COMPLETED".equals(normalizedStatus)) {
             if (job.getCatfactRef() == null || job.getCatfactRef().isBlank()) {
                 return EvaluationOutcome.fail("Completed job must reference a CatFact (catfactRef missing)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (job.getTargetCount() == null) {
                 return EvaluationOutcome.fail("Completed job must have targetCount set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getTargetCount() < 0) {
                 return EvaluationOutcome.fail("targetCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // If targetCount is zero, mark as data quality failure as no recipients were delivered to
             if (job.getTargetCount() == 0) {
                 return EvaluationOutcome.fail("Completed job reported zero recipients (targetCount == 0)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // All checks for a successful delivery job passed
             return EvaluationOutcome.success();
         }

         // Explicit failure state
         if ("FAILED".equals(normalizedStatus)) {
             return EvaluationOutcome.fail("Job ended in FAILED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Any other state (SCHEDULED, FETCHING, FACT_READY, SENDING, etc.) is not considered a successful delivery
         return EvaluationOutcome.fail("Delivery not completed. Current status: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}