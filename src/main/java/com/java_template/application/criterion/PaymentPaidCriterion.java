package com.java_template.application.criterion;

import com.java_template.application.entity.payment.version_1.Payment;
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
public class PaymentPaidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentPaidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking payment paid criterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePaymentIsPaid)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePaymentIsPaid(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();

        // Check if payment exists
        if (payment == null) {
            return EvaluationOutcome.fail("Payment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if payment ID is present
        if (payment.getPaymentId() == null || payment.getPaymentId().isBlank()) {
            return EvaluationOutcome.fail("Payment ID is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if payment status is PAID
        if (!"PAID".equals(payment.getStatus())) {
            return EvaluationOutcome.fail("Payment is not in PAID status, current status: " + payment.getStatus(),
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Payment {} is confirmed as PAID", payment.getPaymentId());
        return EvaluationOutcome.success();
    }
}