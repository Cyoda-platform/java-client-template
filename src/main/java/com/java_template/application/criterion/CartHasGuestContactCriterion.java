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
 * CartHasGuestContactCriterion - Validates that cart has complete guest contact information
 * 
 * This criterion validates that a cart has complete guest contact with required address fields
 * before allowing conversion to order.
 * Used in transition: CHECKOUT
 */
@Component
public class CartHasGuestContactCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartHasGuestContactCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking cart has guest contact criteria for request: {}", request.getId());
        
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
     * Main validation logic - checks if cart has complete guest contact information
     */
    private EvaluationOutcome validateCartHasGuestContact(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entityWithMetadata().entity();

        // Check if cart is null
        if (cart == null) {
            logger.warn("Cart is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if guest contact exists
        if (cart.getGuestContact() == null) {
            logger.warn("Cart {} has no guest contact", cart.getCartId());
            return EvaluationOutcome.fail("Cart must have guest contact information before checkout", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Cart.GuestContact guestContact = cart.getGuestContact();

        // Check required name
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            logger.warn("Cart {} guest contact has no name", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact must have a name", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check required address
        if (guestContact.getAddress() == null) {
            logger.warn("Cart {} guest contact has no address", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact must have an address", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Cart.Address address = guestContact.getAddress();

        // Check required address fields
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no line1", cart.getCartId());
            return EvaluationOutcome.fail("Guest address must have line1", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no city", cart.getCartId());
            return EvaluationOutcome.fail("Guest address must have city", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no postcode", cart.getCartId());
            return EvaluationOutcome.fail("Guest address must have postcode", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            logger.warn("Cart {} guest address has no country", cart.getCartId());
            return EvaluationOutcome.fail("Guest address must have country", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Cart {} has valid guest contact information", cart.getCartId());
        return EvaluationOutcome.success();
    }
}
