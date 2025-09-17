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
 * HasItemsCriterion - Validates that cart has items before checkout
 * 
 * This criterion checks that the cart has at least one item with valid
 * quantity and price before allowing transition to checkout state.
 */
@Component
public class HasItemsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HasItemsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HasItems criteria for cart request: {}", request.getId());
        
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
     * Validates that the cart has valid items
     */
    private EvaluationOutcome validateCartHasItems(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entityWithMetadata().entity();

        // Check if cart is null
        if (cart == null) {
            logger.warn("Cart is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if cart is valid
        if (!cart.isValid()) {
            logger.warn("Cart is not valid");
            return EvaluationOutcome.fail("Cart is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if cart has lines
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            logger.warn("Cart {} has no items", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if cart has valid items with quantity > 0
        boolean hasValidItems = cart.getLines().stream()
                .anyMatch(line -> line.getQty() != null && line.getQty() > 0 &&
                                line.getPrice() != null && line.getPrice() > 0);

        if (!hasValidItems) {
            logger.warn("Cart {} has no valid items with quantity > 0", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no valid items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check total items matches line items
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            logger.warn("Cart {} has invalid total items: {}", cart.getCartId(), cart.getTotalItems());
            return EvaluationOutcome.fail("Cart has invalid total items", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Cart {} has valid items: {} total items", cart.getCartId(), cart.getTotalItems());
        return EvaluationOutcome.success();
    }
}
