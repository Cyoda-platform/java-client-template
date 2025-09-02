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

import java.util.regex.Pattern;

@Component
public class CartHasGuestContactCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    // Basic email pattern for validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public CartHasGuestContactCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if cart has guest contact for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartHasGuestContact)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCartHasGuestContact(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        // Check if cart is null
        if (cart == null) {
            logger.warn("Cart entity is null");
            return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if guest contact is present
        if (cart.getGuestContact() == null) {
            logger.warn("Cart {} has no guest contact information", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact information is required for checkout", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Cart.GuestContact contact = cart.getGuestContact();

        // Validate required name
        if (isNullOrBlank(contact.getName())) {
            logger.warn("Cart {} guest contact has no name", cart.getCartId());
            return EvaluationOutcome.fail("Guest name is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate required address
        if (contact.getAddress() == null) {
            logger.warn("Cart {} guest contact has no address", cart.getCartId());
            return EvaluationOutcome.fail("Shipping address is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Cart.Address address = contact.getAddress();

        // Validate required address fields
        if (isNullOrBlank(address.getLine1())) {
            logger.warn("Cart {} guest contact address has no line1", cart.getCartId());
            return EvaluationOutcome.fail("Address line 1 is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (isNullOrBlank(address.getCity())) {
            logger.warn("Cart {} guest contact address has no city", cart.getCartId());
            return EvaluationOutcome.fail("City is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (isNullOrBlank(address.getPostcode())) {
            logger.warn("Cart {} guest contact address has no postcode", cart.getCartId());
            return EvaluationOutcome.fail("Postal code is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (isNullOrBlank(address.getCountry())) {
            logger.warn("Cart {} guest contact address has no country", cart.getCartId());
            return EvaluationOutcome.fail("Country is required", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Optional field validations (warn but don't fail)
        if (!isNullOrBlank(contact.getEmail())) {
            if (!EMAIL_PATTERN.matcher(contact.getEmail()).matches()) {
                logger.warn("Cart {} guest contact has invalid email format: {}", cart.getCartId(), contact.getEmail());
            }
        }

        if (!isNullOrBlank(contact.getPhone())) {
            if (contact.getPhone().length() < 10) {
                logger.warn("Cart {} guest contact phone number seems too short: {}", cart.getCartId(), contact.getPhone());
            }
        }

        logger.debug("Cart {} has valid guest contact information", cart.getCartId());
        return EvaluationOutcome.success();
    }

    private boolean isNullOrBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
