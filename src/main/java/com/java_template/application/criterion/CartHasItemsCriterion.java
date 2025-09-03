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
        logger.info("Checking cart has items for request: {}", request.getId());
        
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

        logger.debug("Validating cart has items: {}", cart != null ? cart.getCartId() : "null");

        // CRITICAL: Use cart getters directly - never extract from payload
        
        // 1. Validate cart entity exists
        if (cart == null) {
            logger.warn("Cart entity is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 2. Check cart.lines is not null
        if (cart.getLines() == null) {
            logger.warn("Cart {} has null lines", cart.getCartId());
            return EvaluationOutcome.fail("Cart lines array is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 3. Check cart.lines array is not empty
        if (cart.getLines().isEmpty()) {
            logger.warn("Cart {} has empty lines array", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 4. Check cart.totalItems > 0
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            logger.warn("Cart {} has invalid total items: {}", cart.getCartId(), cart.getTotalItems());
            return EvaluationOutcome.fail("Cart total items is zero or negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 5. Validate each line has positive quantity
        for (Cart.CartLine line : cart.getLines()) {
            if (line == null) {
                logger.warn("Cart {} has null line", cart.getCartId());
                return EvaluationOutcome.fail("Cart contains null line item", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            
            if (line.getQty() == null || line.getQty() <= 0) {
                logger.warn("Cart {} has line with invalid quantity: SKU={}, qty={}", 
                    cart.getCartId(), line.getSku(), line.getQty());
                return EvaluationOutcome.fail("Cart line has zero or negative quantity: " + line.getSku(), 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            
            // Additional validation for line completeness
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                logger.warn("Cart {} has line with missing SKU", cart.getCartId());
                return EvaluationOutcome.fail("Cart line missing SKU", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            
            if (line.getName() == null || line.getName().trim().isEmpty()) {
                logger.warn("Cart {} has line with missing name: SKU={}", cart.getCartId(), line.getSku());
                return EvaluationOutcome.fail("Cart line missing name: " + line.getSku(), 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            
            if (line.getPrice() == null || line.getPrice() < 0) {
                logger.warn("Cart {} has line with invalid price: SKU={}, price={}", 
                    cart.getCartId(), line.getSku(), line.getPrice());
                return EvaluationOutcome.fail("Cart line has invalid price: " + line.getSku(), 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        logger.info("Cart {} validation passed: {} items in {} lines", 
            cart.getCartId(), cart.getTotalItems(), cart.getLines().size());
        
        return EvaluationOutcome.success();
    }
}
