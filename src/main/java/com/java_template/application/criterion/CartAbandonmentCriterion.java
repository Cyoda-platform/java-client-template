package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
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
public class CartAbandonmentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartAbandonmentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        if (cart == null) {
            return EvaluationOutcome.fail("Cart missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (cart.getLastActivityAt() == null) {
            return EvaluationOutcome.fail("lastActivityAt missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            Instant last = Instant.parse(cart.getLastActivityAt());
            Duration since = Duration.between(last, Instant.now());
            // default abandonment TTL = 7 days
            if (since.toDays() >= 7) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Cart not yet abandoned", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (DateTimeParseException ex) {
            logger.warn("Invalid lastActivityAt format for cart {}: {}", cart.getId(), cart.getLastActivityAt());
            return EvaluationOutcome.fail("Invalid lastActivityAt format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
