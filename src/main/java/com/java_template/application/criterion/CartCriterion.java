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

import java.util.Arrays;
import java.util.List;

/**
 * CartCriterion - Validates cart business rules
 * 
 * This criterion validates:
 * - Required fields are present and valid
 * - Cart status is valid
 * - Cart lines are valid
 * - Totals are correctly calculated
 * - Guest contact information is valid when required
 */
@Component
public class CartCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final List<String> VALID_CART_STATUSES = Arrays.asList(
        "NEW", "ACTIVE", "CHECKING_OUT", "CONVERTED"
    );

    public CartCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Cart criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCart)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the Cart entity
     */
    private EvaluationOutcome validateCart(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entityWithMetadata().entity();

        // Check if cart is null (structural validation)
        if (cart == null) {
            logger.warn("Cart is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Basic entity validation
        if (!cart.isValid()) {
            logger.warn("Cart basic validation failed for cartId: {}", cart.getCartId());
            return EvaluationOutcome.fail("Cart basic validation failed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate cart status
        if (!VALID_CART_STATUSES.contains(cart.getStatus())) {
            logger.warn("Invalid cart status: {}", cart.getStatus());
            return EvaluationOutcome.fail("Invalid cart status: " + cart.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate cart lines
        if (cart.getLines() != null) {
            for (Cart.CartLine line : cart.getLines()) {
                EvaluationOutcome lineValidation = validateCartLine(line);
                if (!lineValidation.isSuccess()) {
                    return lineValidation;
                }
            }
        }

        // Validate totals consistency
        EvaluationOutcome totalsValidation = validateTotals(cart);
        if (!totalsValidation.isSuccess()) {
            return totalsValidation;
        }

        // Validate guest contact if cart is in checkout state
        if ("CHECKING_OUT".equals(cart.getStatus()) || "CONVERTED".equals(cart.getStatus())) {
            EvaluationOutcome contactValidation = validateGuestContact(cart.getGuestContact());
            if (!contactValidation.isSuccess()) {
                return contactValidation;
            }
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates individual cart line
     */
    private EvaluationOutcome validateCartLine(Cart.CartLine line) {
        if (line == null) {
            return EvaluationOutcome.fail("Cart line is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (line.getSku() == null || line.getSku().trim().isEmpty()) {
            return EvaluationOutcome.fail("Cart line SKU is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getName() == null || line.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Cart line name is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getPrice() == null || line.getPrice() < 0) {
            return EvaluationOutcome.fail("Cart line price must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getQty() == null || line.getQty() <= 0) {
            return EvaluationOutcome.fail("Cart line quantity must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate line total calculation
        if (line.getLineTotal() != null) {
            double expectedTotal = line.getPrice() * line.getQty();
            if (Math.abs(line.getLineTotal() - expectedTotal) > 0.01) {
                return EvaluationOutcome.fail("Cart line total calculation is incorrect", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates cart totals consistency
     */
    private EvaluationOutcome validateTotals(Cart cart) {
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            if (cart.getTotalItems() != 0 || cart.getGrandTotal() != 0.0) {
                return EvaluationOutcome.fail("Empty cart should have zero totals", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            return EvaluationOutcome.success();
        }

        // Calculate expected totals
        int expectedTotalItems = 0;
        double expectedGrandTotal = 0.0;

        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null) {
                expectedTotalItems += line.getQty();
            }
            if (line.getLineTotal() != null) {
                expectedGrandTotal += line.getLineTotal();
            }
        }

        // Validate total items
        if (!cart.getTotalItems().equals(expectedTotalItems)) {
            return EvaluationOutcome.fail("Cart total items calculation is incorrect", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate grand total (allow small floating point differences)
        if (Math.abs(cart.getGrandTotal() - expectedGrandTotal) > 0.01) {
            return EvaluationOutcome.fail("Cart grand total calculation is incorrect", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates guest contact information
     */
    private EvaluationOutcome validateGuestContact(Cart.GuestContact guestContact) {
        if (guestContact == null) {
            return EvaluationOutcome.fail("Guest contact is required for checkout", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Guest contact name is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate address if present
        if (guestContact.getAddress() != null) {
            Cart.Address address = guestContact.getAddress();
            if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
                return EvaluationOutcome.fail("Address line 1 is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            if (address.getCity() == null || address.getCity().trim().isEmpty()) {
                return EvaluationOutcome.fail("Address city is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
                return EvaluationOutcome.fail("Address postcode is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
                return EvaluationOutcome.fail("Address country is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
