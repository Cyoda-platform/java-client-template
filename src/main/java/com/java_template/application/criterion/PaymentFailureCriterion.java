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
public class PaymentFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentFailureCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionJob> context) {
         AdoptionJob entity = context.entity();

         // If no payment required, there's no payment failure
         if (entity.getFee() == null || entity.getFee() <= 0.0) {
             return EvaluationOutcome.success();
         }

         String status = entity.getStatus();
         String processedAt = entity.getProcessedAt();

         // Definitive payment failure: job moved to failed and no processed timestamp
         if ("failed".equals(status)) {
             return EvaluationOutcome.fail("Payment failed for adoption job: " + (entity.getId() == null ? "<unknown>" : entity.getId()),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data inconsistency: job completed but no processedAt recorded while a fee was required
         if ("completed".equals(status) && (processedAt == null || processedAt.isBlank())) {
             return EvaluationOutcome.fail("Completed job with required fee has no processed timestamp: " + (entity.getId() == null ? "<unknown>" : entity.getId()),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // No payment failure detected
         return EvaluationOutcome.success();
    }
}