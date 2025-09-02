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
        logger.debug("Checking if cart has items for request: {}", request.getId());
        
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
        
        // Check if cart is null
        if (cart == null) {
            logger.warn("Cart entity is null");
            return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if lines array is null or empty
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            logger.warn("Cart {} has no items", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Count valid items (with positive quantity)
        int validItemCount = 0;
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null && line.getQty() > 0) {
                validItemCount++;
            }
        }

        // Check if there are any valid items
        if (validItemCount == 0) {
            logger.warn("Cart {} has no items with positive quantity", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no items with positive quantity", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check consistency between line items and total items
        if (cart.getTotalItems() != null && cart.getTotalItems() <= 0) {
            logger.warn("Cart {} total items is not positive despite having line items", cart.getCartId());
            return EvaluationOutcome.fail("Cart total items is not positive despite having line items", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Cart {} has {} valid items", cart.getCartId(), validItemCount);
        return EvaluationOutcome.success();
    }
}
