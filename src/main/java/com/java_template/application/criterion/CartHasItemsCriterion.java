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

/**
 * CartHasItemsCriterion - Checks if cart has at least one item with quantity > 0.
 * 
 * This criterion is used to validate cart before checkout and prevent empty cart operations.
 * It evaluates whether the cart contains any items with positive quantities.
 */
@Component
public class CartHasItemsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartHasItemsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Cart has items criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartHasItems)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the cart has at least one item with quantity > 0
     */
    private EvaluationOutcome validateCartHasItems(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entityWithMetadata().entity();

        // Check if cart is null (structural validation)
        if (cart == null) {
            logger.warn("Cart is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if lines array is null or empty
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            logger.debug("Cart has no lines");
            return EvaluationOutcome.fail("Cart is empty", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if any line has quantity > 0
        boolean hasItems = false;
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null && line.getQty() > 0) {
                hasItems = true;
                break;
            }
        }

        if (!hasItems) {
            logger.debug("Cart has no items with positive quantity");
            return EvaluationOutcome.fail("Cart has no items with positive quantity", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Cart has items validation passed");
        return EvaluationOutcome.success();
    }
}
