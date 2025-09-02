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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

        // Extract guest contact from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        Map<String, Object> guestContactData = createSampleGuestContactData(); // TODO: Extract from request payload

        if (guestContactData == null) {
            throw new IllegalArgumentException("Guest contact information is required for checkout");
        }

        logger.info("Processing checkout for cart: {}", cart.getCartId());

        // Create and validate guest contact
        Cart.GuestContact guestContact = createGuestContactFromData(guestContactData);
        
        if (!guestContact.isValid()) {
            throw new IllegalArgumentException("Guest contact information is incomplete or invalid");
        }

        // Set guest contact on cart
        cart.setGuestContact(guestContact);

        // Final validation of cart totals
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            throw new IllegalStateException("Cart must have items for checkout");
        }
        if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
            throw new IllegalStateException("Cart grand total must be greater than 0");
        }

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart checkout processed successfully: cartId={}, guestName={}, totalItems={}, grandTotal={}",
            cart.getCartId(), guestContact.getName(), cart.getTotalItems(), cart.getGrandTotal());

        return cart;
    }

    private Map<String, Object> createSampleGuestContactData() {
        Map<String, Object> guestContactData = new HashMap<>();
        guestContactData.put("name", "Sample Guest");
        guestContactData.put("email", "guest@example.com");
        guestContactData.put("phone", "+1234567890");

        Map<String, Object> addressData = new HashMap<>();
        addressData.put("line1", "123 Sample Street");
        addressData.put("city", "Sample City");
        addressData.put("postcode", "12345");
        addressData.put("country", "Sample Country");
        guestContactData.put("address", addressData);

        return guestContactData;
    }

    private Cart.GuestContact createGuestContactFromData(Map<String, Object> guestContactData) {
        Cart.GuestContact guestContact = new Cart.GuestContact();
        
        guestContact.setName((String) guestContactData.get("name"));
        guestContact.setEmail((String) guestContactData.get("email"));
        guestContact.setPhone((String) guestContactData.get("phone"));

        @SuppressWarnings("unchecked")
        Map<String, Object> addressData = (Map<String, Object>) guestContactData.get("address");
        if (addressData != null) {
            Cart.GuestAddress address = new Cart.GuestAddress();
            address.setLine1((String) addressData.get("line1"));
            address.setCity((String) addressData.get("city"));
            address.setPostcode((String) addressData.get("postcode"));
            address.setCountry((String) addressData.get("country"));
            guestContact.setAddress(address);
        }

        return guestContact;
    }
}
