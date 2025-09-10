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
 * CartHasItemsCriterion - Validates that cart has items before checkout
 * 
 * This criterion validates that a cart has at least one item before allowing checkout to begin.
 * Used in transition: OPEN_CHECKOUT
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
        logger.debug("Checking cart has items criteria for request: {}", request.getId());
        
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
     * Main validation logic - checks if cart has at least one item with quantity > 0
     */
    private EvaluationOutcome validateCartHasItems(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entityWithMetadata().entity();

        // Check if cart is null
        if (cart == null) {
            logger.warn("Cart is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if cart lines exist
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            logger.warn("Cart {} has no lines", cart.getCartId());
            return EvaluationOutcome.fail("Cart must have items before checkout", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if at least one line has quantity > 0
        boolean hasValidItems = false;
        for (Cart.CartLine line : cart.getLines()) {
            if (line != null && line.getQty() != null && line.getQty() > 0) {
                hasValidItems = true;
                break;
            }
        }

        if (!hasValidItems) {
            logger.warn("Cart {} has no items with quantity > 0", cart.getCartId());
            return EvaluationOutcome.fail("Cart must have items with quantity > 0 before checkout", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Cart {} has valid items for checkout", cart.getCartId());
        return EvaluationOutcome.success();
    }
}
