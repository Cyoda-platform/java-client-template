package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class CartCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartCheckoutProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart checkout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCheckout)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processCheckout(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Processing checkout for cart: {}", cart.getCartId());
        
        // Extract guest contact from context
        Cart.GuestContact guestContact = extractGuestContactFromContext(context);
        
        if (guestContact == null) {
            throw new IllegalArgumentException("Guest contact information is required");
        }
        
        // Validate guest contact information
        validateGuestContact(guestContact);
        
        // Attach guest contact to cart
        cart.setGuestContact(guestContact);
        
        // Update timestamps
        cart.setUpdatedAt(Instant.now());
        
        logger.info("Cart checkout completed successfully: {}", cart.getCartId());
        return cart;
    }

    private Cart.GuestContact extractGuestContactFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        // TODO: Extract from context - placeholder implementation
        return null;
    }

    private void validateGuestContact(Cart.GuestContact guestContact) {
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Guest name is required");
        }
        
        if (guestContact.getAddress() == null) {
            throw new IllegalArgumentException("Address is required");
        }
        
        Cart.Address address = guestContact.getAddress();
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            throw new IllegalArgumentException("Address line 1 is required");
        }
        
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            throw new IllegalArgumentException("City is required");
        }
        
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            throw new IllegalArgumentException("Postcode is required");
        }
        
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            throw new IllegalArgumentException("Country is required");
        }
    }
}
