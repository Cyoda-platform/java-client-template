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
public class PaymentAutoMarkPaidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentAutoMarkPaidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking payment auto mark paid criterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePaymentReadyForAutoPaid)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePaymentReadyForAutoPaid(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();

        // Check if payment exists
        if (payment == null) {
            return EvaluationOutcome.fail("Payment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if payment is in INITIATED status
        if (!"INITIATED".equals(payment.getStatus())) {
            return EvaluationOutcome.fail("Payment is not in INITIATED status", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if payment has creation time
        if (payment.getCreatedAt() == null || payment.getCreatedAt().isBlank()) {
            return EvaluationOutcome.fail("Payment creation time is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if 3 seconds have passed since creation
        try {
            Instant createdAt = Instant.parse(payment.getCreatedAt());
            Instant now = Instant.now();
            long secondsPassed = ChronoUnit.SECONDS.between(createdAt, now);

            if (secondsPassed >= 3) {
                logger.info("Payment {} is ready for auto-paid after {} seconds", payment.getPaymentId(), secondsPassed);
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Payment not ready for auto-paid, only " + secondsPassed + " seconds passed",
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } catch (Exception e) {
            return EvaluationOutcome.fail("Error parsing payment creation time: " + e.getMessage(),
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}