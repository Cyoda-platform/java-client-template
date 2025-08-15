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

import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeParseException;

@Component
public class PaymentExpiredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentExpiredCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
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
            return EvaluationOutcome.fail("Order missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (order.getCreatedAt() == null) {
            return EvaluationOutcome.fail("createdAt missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            Instant created = Instant.parse(order.getCreatedAt());
            Duration since = Duration.between(created, Instant.now());
            // if payment failed and more than 7 days passed -> expired
            if ("FAILED".equals(order.getPaymentStatus()) && since.toDays() >= 7) {
                return EvaluationOutcome.success();
            }
            return EvaluationOutcome.fail("Payment not expired or not in failed state", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } catch (DateTimeParseException ex) {
            logger.warn("Invalid createdAt format for order {}: {}", order.getId(), order.getCreatedAt());
            return EvaluationOutcome.fail("Invalid createdAt format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
