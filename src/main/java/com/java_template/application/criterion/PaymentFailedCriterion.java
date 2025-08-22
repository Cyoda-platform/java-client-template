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
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
         Order entity = context.entity(); // Order is the subject of this criterion.
         if (entity == null) {
             logger.warn("PaymentFailedCriterion received null entity in evaluation context");
             return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String paymentStatus = entity.getPaymentStatus();
         String orderId = entity.getId() != null ? entity.getId() : "unknown";

         if (paymentStatus == null || paymentStatus.isBlank()) {
             logger.warn("Order {} has missing paymentStatus", orderId);
             return EvaluationOutcome.fail(String.format("Order [%s] missing paymentStatus", orderId), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String normalized = paymentStatus.trim();
         if ("Failed".equalsIgnoreCase(normalized)) {
             logger.info("Order {} evaluated: paymentStatus='Failed'", orderId);
             // Criterion "PaymentFailed" is satisfied when paymentStatus == Failed
             return EvaluationOutcome.success();
         }

         if ("Paid".equalsIgnoreCase(normalized)) {
             logger.debug("Order {} evaluated: paymentStatus='Paid' - not a failed payment", orderId);
             return EvaluationOutcome.fail(String.format("Order [%s] payment already succeeded", orderId), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if ("Pending".equalsIgnoreCase(normalized)) {
             logger.debug("Order {} evaluated: paymentStatus='Pending' - payment not failed", orderId);
             return EvaluationOutcome.fail(String.format("Order [%s] payment still pending", orderId), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         logger.debug("Order {} evaluated: unknown paymentStatus='{}'", orderId, paymentStatus);
         return EvaluationOutcome.fail(String.format("Order [%s] has unknown paymentStatus '%s'", orderId, paymentStatus), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}