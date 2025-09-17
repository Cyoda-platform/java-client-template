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
 * HasGuestContactCriterion - Validates that cart has guest contact info for checkout
 * 
 * This criterion checks that the cart has valid guest contact information
 * including name and address before allowing final checkout transition.
 */
@Component
public class HasGuestContactCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HasGuestContactCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HasGuestContact criteria for cart request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartHasGuestContact)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the cart has valid guest contact information
     */
    private EvaluationOutcome validateCartHasGuestContact(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
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

        // Check if guest contact exists
        if (cart.getGuestContact() == null) {
            logger.warn("Cart {} has no guest contact information", cart.getCartId());
            return EvaluationOutcome.fail("Cart has no guest contact information", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Cart.GuestContact guestContact = cart.getGuestContact();

        // Check if guest has name
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            logger.warn("Cart {} guest contact has no name", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact has no name", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if guest has address
        if (guestContact.getAddress() == null) {
            logger.warn("Cart {} guest contact has no address", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact has no address", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Cart.GuestAddress address = guestContact.getAddress();

        // Check required address fields
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no line1", cart.getCartId());
            return EvaluationOutcome.fail("Guest address has no line1", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no city", cart.getCartId());
            return EvaluationOutcome.fail("Guest address has no city", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no postcode", cart.getCartId());
            return EvaluationOutcome.fail("Guest address has no postcode", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no country", cart.getCartId());
            return EvaluationOutcome.fail("Guest address has no country", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Cart {} has valid guest contact information", cart.getCartId());
        return EvaluationOutcome.success();
    }
}
