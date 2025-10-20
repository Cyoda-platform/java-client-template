package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor for handling cart checkout, validating cart state,
 * guest contact information, and preparing cart for order conversion.
 */
@Component
public class CheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CheckoutProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processCheckout)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Cart
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && cart.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for checkout processing
     */
    private EntityWithMetadata<Cart> processCheckout(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Processing checkout for cartId: {}", cart.getCartId());

        // Validate checkout prerequisites
        validateCheckoutPrerequisites(cart);

        // Validate guest contact information
        validateGuestContact(cart);

        // Final totals validation
        validateCartTotals(cart);

        // Update status to CONVERTED
        cart.setStatus("CONVERTED");
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} successfully checked out and converted", cart.getCartId());

        return entityWithMetadata;
    }

    /**
     * Validate checkout prerequisites
     */
    private void validateCheckoutPrerequisites(Cart cart) {
        // Check cart has items
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            throw new IllegalStateException("Cannot checkout empty cart: " + cart.getCartId());
        }

        // Check cart is in correct state
        if (!"CHECKING_OUT".equals(cart.getStatus())) {
            throw new IllegalStateException("Cart must be in CHECKING_OUT status to checkout: " + cart.getCartId());
        }

        // Check all lines have valid data
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                throw new IllegalStateException("Cart line missing SKU in cart: " + cart.getCartId());
            }
            if (line.getQty() == null || line.getQty() <= 0) {
                throw new IllegalStateException("Cart line has invalid quantity for SKU " + line.getSku() + " in cart: " + cart.getCartId());
            }
            if (line.getPrice() == null || line.getPrice() < 0) {
                throw new IllegalStateException("Cart line has invalid price for SKU " + line.getSku() + " in cart: " + cart.getCartId());
            }
        }

        logger.debug("Checkout prerequisites validated for cart: {}", cart.getCartId());
    }

    /**
     * Validate guest contact information
     */
    private void validateGuestContact(Cart cart) {
        if (cart.getGuestContact() == null) {
            throw new IllegalStateException("Guest contact information required for checkout: " + cart.getCartId());
        }

        Cart.CartGuestContact contact = cart.getGuestContact();
        
        // Name is required
        if (contact.getName() == null || contact.getName().trim().isEmpty()) {
            throw new IllegalStateException("Guest name is required for checkout: " + cart.getCartId());
        }

        // Address is required
        if (contact.getAddress() == null) {
            throw new IllegalStateException("Guest address is required for checkout: " + cart.getCartId());
        }

        Cart.CartAddress address = contact.getAddress();
        
        // Required address fields
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            throw new IllegalStateException("Address line1 is required for checkout: " + cart.getCartId());
        }
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            throw new IllegalStateException("Address city is required for checkout: " + cart.getCartId());
        }
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            throw new IllegalStateException("Address postcode is required for checkout: " + cart.getCartId());
        }
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            throw new IllegalStateException("Address country is required for checkout: " + cart.getCartId());
        }

        logger.debug("Guest contact validated for cart: {}", cart.getCartId());
    }

    /**
     * Validate cart totals are correct
     */
    private void validateCartTotals(Cart cart) {
        // Recalculate totals to verify they are correct
        int calculatedItems = 0;
        double calculatedTotal = 0.0;

        for (Cart.CartLine line : cart.getLines()) {
            calculatedItems += line.getQty();
            calculatedTotal += line.getPrice() * line.getQty();
        }

        // Check totals match
        if (!cart.getTotalItems().equals(calculatedItems)) {
            throw new IllegalStateException("Cart total items mismatch in cart: " + cart.getCartId() + 
                                          ", expected: " + calculatedItems + ", actual: " + cart.getTotalItems());
        }

        if (Math.abs(cart.getGrandTotal() - calculatedTotal) > 0.01) { // Allow for small floating point differences
            throw new IllegalStateException("Cart grand total mismatch in cart: " + cart.getCartId() + 
                                          ", expected: " + calculatedTotal + ", actual: " + cart.getGrandTotal());
        }

        logger.debug("Cart totals validated for cart: {}", cart.getCartId());
    }
}
