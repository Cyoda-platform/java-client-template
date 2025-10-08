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

import java.math.BigDecimal;

/**
 * ABOUTME: This criterion validates payment entities against business rules
 * for payment amounts, dates, and references.
 */
@Component
public class PaymentValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Payment validation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    public EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (payment == null) {
            logger.warn("Payment entity is null");
            return EvaluationOutcome.fail("Payment entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!payment.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Payment entity is not valid: {}", payment.getPaymentId());
            return EvaluationOutcome.fail("Payment entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Business rule: Payment amount should be positive
        if (payment.getPaymentAmount() != null && payment.getPaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Payment amount is not positive: {}", payment.getPaymentAmount());
            return EvaluationOutcome.fail("Payment amount must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
