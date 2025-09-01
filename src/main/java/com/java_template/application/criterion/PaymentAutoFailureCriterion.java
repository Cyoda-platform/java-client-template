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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class PaymentAutoFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentAutoFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking payment auto failure criterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePaymentShouldFail)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePaymentShouldFail(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();

        // Check if payment exists
        if (payment == null) {
            return EvaluationOutcome.fail("Payment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if payment is in INITIATED status
        if (!"INITIATED".equals(payment.getStatus())) {
            return EvaluationOutcome.fail("Payment is not in INITIATED status", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // For demo purposes, this criterion could check for specific failure conditions
        // For now, we'll implement a simple timeout-based failure (e.g., after 30 seconds)
        if (payment.getCreatedAt() != null) {
            try {
                Instant createdAt = Instant.parse(payment.getCreatedAt());
                Instant now = Instant.now();
                long secondsPassed = ChronoUnit.SECONDS.between(createdAt, now);

                // Auto-fail after 30 seconds for demo purposes
                if (secondsPassed >= 30) {
                    logger.info("Payment {} should auto-fail after {} seconds", payment.getPaymentId(), secondsPassed);
                    return EvaluationOutcome.success();
                }
            } catch (Exception e) {
                logger.error("Error parsing payment creation time: {}", e.getMessage());
            }
        }

        // Payment should not fail yet
        return EvaluationOutcome.fail("Payment should not auto-fail yet", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}