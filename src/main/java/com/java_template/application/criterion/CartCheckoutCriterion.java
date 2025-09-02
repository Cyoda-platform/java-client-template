package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CartCheckoutCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public CartCheckoutCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating CartCheckoutCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartCheckout)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCartCheckout(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Validating cart checkout criteria for cart: {}", cart != null ? cart.getCartId() : "null");

        if (cart == null) {
            return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check cart.lines is not empty
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Cart is empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check all cart lines have valid SKUs
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return EvaluationOutcome.fail("Invalid SKU in cart line", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Check all cart lines have qty > 0
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() == null || line.getQty() <= 0) {
                return EvaluationOutcome.fail("Invalid quantity in cart line for SKU: " + line.getSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Check cart.totalItems > 0
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            return EvaluationOutcome.fail("Cart totals are invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check cart.grandTotal > 0
        if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
            return EvaluationOutcome.fail("Cart totals are invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // For each line, verify product exists and has sufficient stock
        try {
            for (Cart.CartLine line : cart.getLines()) {
                EntityResponse<Product> productResponse = entityService.findByBusinessId(Product.class, line.getSku());
                Product product = productResponse.getData();
                
                if (product == null) {
                    return EvaluationOutcome.fail("Product not found for SKU: " + line.getSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
                }

                if (product.getQuantityAvailable() < line.getQty()) {
                    return EvaluationOutcome.fail("Insufficient stock for product " + line.getSku() + 
                        ": required " + line.getQty() + ", available " + product.getQuantityAvailable(), 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }

            logger.info("Cart checkout validation passed: cartId={}, totalItems={}, grandTotal={}", 
                cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Failed to validate cart checkout: {}", e.getMessage());
            return EvaluationOutcome.fail("Failed to validate cart checkout: " + e.getMessage(), StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
