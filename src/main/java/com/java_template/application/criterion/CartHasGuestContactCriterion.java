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
    
    // Basic email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    // Basic phone validation pattern (allows various formats)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[0-9\\s\\-\\(\\)]{7,20}$"
    );

    public CartHasGuestContactCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking cart has guest contact for request: {}", request.getId());
        
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

        logger.debug("Validating cart has guest contact: {}", cart != null ? cart.getCartId() : "null");

        // CRITICAL: Use cart getters directly - never extract from payload
        
        // 1. Validate cart entity exists
        if (cart == null) {
            logger.warn("Cart entity is null");
            return EvaluationOutcome.fail("Cart is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 2. Check cart.guestContact is not null
        if (cart.getGuestContact() == null) {
            logger.warn("Cart {} has no guest contact", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Cart.GuestContact guestContact = cart.getGuestContact();

        // 3. Validate required contact fields
        
        // 3a. guestContact.name is not null and not empty
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            logger.warn("Cart {} guest contact has missing or empty name", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 3b. guestContact.address is not null
        if (guestContact.getAddress() == null) {
            logger.warn("Cart {} guest contact has no address", cart.getCartId());
            return EvaluationOutcome.fail("Guest contact address is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Cart.Address address = guestContact.getAddress();

        // 3c. address.line1 is not null and not empty
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            logger.warn("Cart {} guest contact address missing line1", cart.getCartId());
            return EvaluationOutcome.fail("Address line 1 is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 3d. address.city is not null and not empty
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            logger.warn("Cart {} guest contact address missing city", cart.getCartId());
            return EvaluationOutcome.fail("Address city is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 3e. address.postcode is not null and not empty
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            logger.warn("Cart {} guest contact address missing postcode", cart.getCartId());
            return EvaluationOutcome.fail("Address postcode is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 3f. address.country is not null and not empty
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            logger.warn("Cart {} guest contact address missing country", cart.getCartId());
            return EvaluationOutcome.fail("Address country is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 4. Validate optional fields if present
        
        // 4a. If email provided, validate email format
        if (guestContact.getEmail() != null && !guestContact.getEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(guestContact.getEmail().trim()).matches()) {
                logger.warn("Cart {} guest contact has invalid email format: {}", 
                    cart.getCartId(), guestContact.getEmail());
                return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // 4b. If phone provided, validate phone format
        if (guestContact.getPhone() != null && !guestContact.getPhone().trim().isEmpty()) {
            if (!PHONE_PATTERN.matcher(guestContact.getPhone().trim()).matches()) {
                logger.warn("Cart {} guest contact has invalid phone format: {}", 
                    cart.getCartId(), guestContact.getPhone());
                return EvaluationOutcome.fail("Invalid phone format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        logger.info("Cart {} guest contact validation passed: name={}, email={}, phone={}, address complete", 
            cart.getCartId(), guestContact.getName(), 
            guestContact.getEmail() != null ? "provided" : "not provided",
            guestContact.getPhone() != null ? "provided" : "not provided");
        
        return EvaluationOutcome.success();
    }
}
