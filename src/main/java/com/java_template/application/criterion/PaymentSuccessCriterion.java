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
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order order = context.entity();
         if (order == null) {
             logger.warn("PaymentSuccessCriterion: received null order in evaluation context");
             return EvaluationOutcome.fail("Order entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String paymentStatus = order.getPaymentStatus();
         if (paymentStatus == null || paymentStatus.isBlank()) {
             String msg = String.format("Order [%s] missing paymentStatus", order.getId());
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Success path: paymentStatus indicates payment completed
         if ("Paid".equalsIgnoreCase(paymentStatus) || "PAID".equals(paymentStatus) || "paid".equalsIgnoreCase(paymentStatus)) {
             logger.debug("Order [{}] paymentStatus is Paid -> criterion success", order.getId());
             return EvaluationOutcome.success();
         }

         // Explicit failure paths
         if ("Failed".equalsIgnoreCase(paymentStatus)) {
             String msg = String.format("Order [%s] payment failed", order.getId());
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if ("Pending".equalsIgnoreCase(paymentStatus) || "PendingPayment".equalsIgnoreCase(paymentStatus)
             || "WAITING_FOR_PAYMENT".equalsIgnoreCase(paymentStatus) || "WaitingForPayment".equalsIgnoreCase(paymentStatus)) {
             String msg = String.format("Order [%s] payment still pending", order.getId());
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Unknown status - treat as business rule failure (unexpected state)
         String msg = String.format("Order [%s] has unknown paymentStatus '%s'", order.getId(), paymentStatus);
         logger.debug(msg);
         return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}