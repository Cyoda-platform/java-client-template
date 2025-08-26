package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class DeliveryFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeliveryFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Subscriber entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If subscriber is not active, delivery failures are not actionable here
         Boolean active = entity.getActive();
         if (active == null) {
             return EvaluationOutcome.fail("Subscriber.active must be set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (!active) {
             return EvaluationOutcome.success();
         }

         String lastDeliveryStatus = entity.getLastDeliveryStatus();
         if (lastDeliveryStatus == null || lastDeliveryStatus.isBlank()) {
             return EvaluationOutcome.fail("lastDeliveryStatus is required for active subscribers", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Normalize status checks
         String status = lastDeliveryStatus.trim();

         if ("FAILED".equalsIgnoreCase(status)) {
             // If subscriber actively opted out, treat as non-actionable (no remediation)
             String optOutAt = entity.getOptOutAt();
             if (optOutAt != null && !optOutAt.isBlank()) {
                 return EvaluationOutcome.success();
             }
             // Active subscriber with failed delivery requires remediation (retry/disable)
             return EvaluationOutcome.fail("Delivery failure detected for active subscriber", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if ("PENDING".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
             return EvaluationOutcome.success();
         }

         // Unknown status value -> data quality issue
         return EvaluationOutcome.fail("Unknown lastDeliveryStatus value: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}