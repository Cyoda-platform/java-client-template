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
        logger.info("Checking cart has items criterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartHasItems)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCartHasItems(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Validating cart has items for cart: {}", cart.getCartId());

        // Check if cart has lines
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            logger.warn("Cart has no items: {}", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Count valid items with quantity > 0
        int validItemCount = 0;
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null && line.getQty() > 0) {
                validItemCount++;
            }
        }

        if (validItemCount == 0) {
            logger.warn("Cart has no valid items with quantity > 0: {}", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no valid items with quantity > 0", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check total items
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            logger.warn("Cart total items is zero or negative: {}", cart.getCartId());
            return EvaluationOutcome.fail("Cart total items is zero or negative", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.info("Cart has valid items: {} items in cart: {}", validItemCount, cart.getCartId());
        return EvaluationOutcome.success();
    }
}
