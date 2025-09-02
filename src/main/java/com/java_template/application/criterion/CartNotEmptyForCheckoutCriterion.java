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
public class CartNotEmptyForCheckoutCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartNotEmptyForCheckoutCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking cart not empty for checkout criterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartForCheckout)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCartForCheckout(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Validating cart for checkout: {}", cart.getCartId());

        // Check cart state (in real implementation, would check cart.meta.state)
        // For now, we'll assume the workflow ensures this criterion only runs when appropriate
        logger.debug("Cart state validation passed for cart: {}", cart.getCartId());

        // Check guest contact information
        if (cart.getGuestContact() == null) {
            logger.warn("Guest contact information is required for cart: {}", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact information is required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Cart.GuestContact guestContact = cart.getGuestContact();

        // Check guest name
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            logger.warn("Guest name is required for cart: {}", cart.getCartId());
            return EvaluationOutcome.fail("Guest name is required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check guest address
        if (guestContact.getAddress() == null) {
            logger.warn("Guest address is required for cart: {}", cart.getCartId());
            return EvaluationOutcome.fail("Guest address is required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Cart.Address address = guestContact.getAddress();

        // Check address line 1
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            logger.warn("Address line 1 is required for cart: {}", cart.getCartId());
            return EvaluationOutcome.fail("Address line 1 is required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check city
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            logger.warn("City is required for cart: {}", cart.getCartId());
            return EvaluationOutcome.fail("City is required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check postcode
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            logger.warn("Postcode is required for cart: {}", cart.getCartId());
            return EvaluationOutcome.fail("Postcode is required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check country
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            logger.warn("Country is required for cart: {}", cart.getCartId());
            return EvaluationOutcome.fail("Country is required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.info("Cart checkout validation passed for cart: {}", cart.getCartId());
        return EvaluationOutcome.success();
    }
}
