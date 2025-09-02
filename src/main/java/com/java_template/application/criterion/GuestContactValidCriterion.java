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
public class GuestContactValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    // Phone validation pattern (basic international format)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[1-9]\\d{1,14}$"
    );

    public GuestContactValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating GuestContactValidCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateGuestContact)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateGuestContact(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Validating guest contact for cart: {}", cart != null ? cart.getCartId() : "null");

        if (cart == null) {
            return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Cart.GuestContact guestContact = cart.getGuestContact();

        // Check guestContact is not null
        if (guestContact == null) {
            return EvaluationOutcome.fail("Guest contact information is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check guestContact.name is not null/empty
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Guest name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check guestContact.address is not null
        if (guestContact.getAddress() == null) {
            return EvaluationOutcome.fail("Shipping address is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Cart.GuestAddress address = guestContact.getAddress();

        // Check guestContact.address.line1 is not null/empty
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            return EvaluationOutcome.fail("Address line 1 is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check guestContact.address.city is not null/empty
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            return EvaluationOutcome.fail("City is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check guestContact.address.postcode is not null/empty
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            return EvaluationOutcome.fail("Postal code is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check guestContact.address.country is not null/empty
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            return EvaluationOutcome.fail("Country is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If email provided, validate email format
        if (guestContact.getEmail() != null && !guestContact.getEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(guestContact.getEmail()).matches()) {
                return EvaluationOutcome.fail("Invalid email format: " + guestContact.getEmail(), 
                    StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // If phone provided, validate phone format
        if (guestContact.getPhone() != null && !guestContact.getPhone().trim().isEmpty()) {
            String cleanPhone = guestContact.getPhone().replaceAll("[\\s\\-\\(\\)]", "");
            if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
                return EvaluationOutcome.fail("Invalid phone format: " + guestContact.getPhone(), 
                    StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        logger.info("Guest contact validation passed: name={}, email={}, phone={}", 
            guestContact.getName(), guestContact.getEmail(), guestContact.getPhone());

        return EvaluationOutcome.success();
    }
}
