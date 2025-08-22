package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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
public class PaymentFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order entity = context.entity();

         if (entity == null) {
             logger.warn("PaymentFailedCriterion invoked with null entity");
             return EvaluationOutcome.fail("Order entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate presence of paymentStatus
         String paymentStatus = entity.getPaymentStatus();
         if (paymentStatus == null || paymentStatus.isBlank()) {
             logger.warn("Order {} has missing paymentStatus", entity.getId());
             return EvaluationOutcome.fail("paymentStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business logic: criterion is satisfied when paymentStatus indicates failure
         if ("Failed".equalsIgnoreCase(paymentStatus.trim())) {
             logger.info("Order {} evaluated: paymentStatus='Failed'", entity.getId());
             return EvaluationOutcome.success();
         }

         // Not a failure state -> criterion not met
         logger.debug("Order {} evaluated: paymentStatus='{}' - not a failed payment", entity.getId(), paymentStatus);
         return EvaluationOutcome.fail("Payment status is not 'Failed' (current: " + paymentStatus + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}