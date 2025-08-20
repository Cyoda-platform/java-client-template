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

@Component
public class CartExpiredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartExpiredCriterion(SerializerFactory serializerFactory) {
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
        if (cart == null) return EvaluationOutcome.fail("Cart not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        try {
            if (cart.getExpiresAt() == null) return EvaluationOutcome.fail("expiresAt not set", StandardEvalReasonCategories.VALIDATION_FAILURE);
            Instant expires = Instant.parse(cart.getExpiresAt());
            if (Instant.now().isAfter(expires)) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Cart not yet expired", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } catch (Exception e) {
            logger.warn("Failed to evaluate cart expiry", e);
            return EvaluationOutcome.fail("Invalid expiresAt format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
