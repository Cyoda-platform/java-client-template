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
public class PaymentValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePayment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePayment(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();

        if (payment == null) {
            return EvaluationOutcome.fail("Payment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (payment.getCartId() == null || payment.getCartId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Payment must have valid cart ID", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            return EvaluationOutcome.fail("Payment amount must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!"DUMMY".equals(payment.getProvider())) {
            return EvaluationOutcome.fail("Invalid payment provider", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
