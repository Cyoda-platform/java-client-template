package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
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
 * GuestContactIsValidCriterion - Validates guest contact information for checkout.
 * 
 * This criterion is used to validate checkout information and ensure order has valid delivery information.
 * It evaluates whether the guest contact has required fields (name, address.line1).
 * 
 * This criterion can work with both Cart and Order entities that have guestContact information.
 */
@Component
public class GuestContactIsValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public GuestContactIsValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Guest contact is valid criteria for request: {}", request.getId());
        
        // Try to evaluate as Cart first, then as Order
        try {
            return serializer.withRequest(request)
                .evaluateEntity(Cart.class, this::validateCartGuestContact)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
        } catch (Exception e) {
            // If Cart evaluation fails, try Order
            return serializer.withRequest(request)
                .evaluateEntity(Order.class, this::validateOrderGuestContact)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates guest contact information for Cart entity
     */
    private EvaluationOutcome validateCartGuestContact(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entityWithMetadata().entity();

        if (cart == null) {
            logger.warn("Cart is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Cart.CartGuestContact guestContact = cart.getGuestContact();
        return validateGuestContactData(guestContact, "Cart");
    }

    /**
     * Validates guest contact information for Order entity
     */
    private EvaluationOutcome validateOrderGuestContact(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Order.OrderGuestContact guestContact = order.getGuestContact();
        return validateOrderGuestContactData(guestContact, "Order");
    }

    /**
     * Common validation logic for Cart guest contact
     */
    private EvaluationOutcome validateGuestContactData(Cart.CartGuestContact guestContact, String entityType) {
        if (guestContact == null) {
            logger.debug("{} guest contact is null", entityType);
            return EvaluationOutcome.fail("Guest contact information is missing", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate name is present
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            logger.debug("{} guest contact name is missing", entityType);
            return EvaluationOutcome.fail("Guest contact name is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate address is present
        if (guestContact.getAddress() == null) {
            logger.debug("{} guest contact address is missing", entityType);
            return EvaluationOutcome.fail("Guest contact address is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate address line1 is present
        if (guestContact.getAddress().getLine1() == null || guestContact.getAddress().getLine1().trim().isEmpty()) {
            logger.debug("{} guest contact address line1 is missing", entityType);
            return EvaluationOutcome.fail("Guest contact address line 1 is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("{} guest contact validation passed", entityType);
        return EvaluationOutcome.success();
    }

    /**
     * Common validation logic for Order guest contact
     */
    private EvaluationOutcome validateOrderGuestContactData(Order.OrderGuestContact guestContact, String entityType) {
        if (guestContact == null) {
            logger.debug("{} guest contact is null", entityType);
            return EvaluationOutcome.fail("Guest contact information is missing", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate name is present
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            logger.debug("{} guest contact name is missing", entityType);
            return EvaluationOutcome.fail("Guest contact name is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate address is present
        if (guestContact.getAddress() == null) {
            logger.debug("{} guest contact address is missing", entityType);
            return EvaluationOutcome.fail("Guest contact address is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate address line1 is present
        if (guestContact.getAddress().getLine1() == null || guestContact.getAddress().getLine1().trim().isEmpty()) {
            logger.debug("{} guest contact address line1 is missing", entityType);
            return EvaluationOutcome.fail("Guest contact address line 1 is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("{} guest contact validation passed", entityType);
        return EvaluationOutcome.success();
    }
}
