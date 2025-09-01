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

import java.math.BigDecimal;

@Component
public class CartValidForCheckoutCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartValidForCheckoutCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking CartValidForCheckoutCriterion for request: {}", request.getId());
        
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
        
        logger.info("Validating cart for checkout: {}", cart != null ? cart.getCartId() : "null");

        // Check if cart entity exists
        if (cart == null) {
            logger.warn("Cart entity not found");
            return EvaluationOutcome.fail("Cart entity not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if cart.lines is not empty
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            logger.warn("Cart must have at least one item");
            return EvaluationOutcome.fail("Cart must have at least one item", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if cart.totalItems > 0
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            logger.warn("Cart must have items. Total items: {}", cart.getTotalItems());
            return EvaluationOutcome.fail("Cart must have items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if cart.grandTotal > 0
        if (cart.getGrandTotal() == null || cart.getGrandTotal().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Cart total must be greater than zero. Grand total: {}", cart.getGrandTotal());
            return EvaluationOutcome.fail("Cart total must be greater than zero", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate each line in cart.lines
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() == null || line.getQty() <= 0) {
                logger.warn("Invalid line quantity for SKU: {}. Quantity: {}", line.getSku(), line.getQty());
                return EvaluationOutcome.fail("Invalid line quantity", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            
            if (line.getPrice() == null || line.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                logger.warn("Invalid line price for SKU: {}. Price: {}", line.getSku(), line.getPrice());
                return EvaluationOutcome.fail("Invalid line price", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Validate totals are correctly calculated
        int expectedTotalItems = cart.getLines().stream()
            .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
            .sum();
        
        BigDecimal expectedGrandTotal = cart.getLines().stream()
            .map(line -> {
                if (line.getPrice() != null && line.getQty() != null) {
                    return line.getPrice().multiply(BigDecimal.valueOf(line.getQty()));
                }
                return BigDecimal.ZERO;
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!cart.getTotalItems().equals(expectedTotalItems)) {
            logger.warn("Invalid total items. Expected: {}, Actual: {}", expectedTotalItems, cart.getTotalItems());
            return EvaluationOutcome.fail("Invalid total items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (cart.getGrandTotal().compareTo(expectedGrandTotal) != 0) {
            logger.warn("Invalid grand total. Expected: {}, Actual: {}", expectedGrandTotal, cart.getGrandTotal());
            return EvaluationOutcome.fail("Invalid grand total", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.info("Cart validation successful for cart: {}", cart.getCartId());
        return EvaluationOutcome.success();
    }
}
