package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Checkout Controller - Anonymous checkout management for OMS
 * 
 * Provides REST endpoints for attaching guest contact information to cart.
 */
@RestController
@RequestMapping("/ui/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;

    public CheckoutController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Attach guest contact information to cart
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> attachGuestContact(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        
        try {
            logger.info("Attaching guest contact to cart: {}", cartId);

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in CHECKING_OUT status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact information
            if (!isValidGuestContact(request.getGuestContact())) {
                logger.warn("Invalid guest contact information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Attach guest contact to cart
            cart.setGuestContact(convertToCartGuestContact(request.getGuestContact()));

            // Update cart (no transition needed, just data update)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Guest contact attached to cart: {} - Name: {}", 
                       cartId, request.getGuestContact().getName());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error attaching guest contact to cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate guest contact information
     */
    private boolean isValidGuestContact(GuestContactRequest guestContact) {
        if (guestContact == null) {
            return false;
        }

        // Name is required
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return false;
        }

        // Address is required
        if (guestContact.getAddress() == null) {
            return false;
        }

        GuestAddressRequest address = guestContact.getAddress();
        
        // Required address fields
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    /**
     * Convert request DTO to Cart entity guest contact
     */
    private Cart.GuestContact convertToCartGuestContact(GuestContactRequest request) {
        Cart.GuestContact guestContact = new Cart.GuestContact();
        guestContact.setName(request.getName());
        guestContact.setEmail(request.getEmail());
        guestContact.setPhone(request.getPhone());

        if (request.getAddress() != null) {
            Cart.GuestAddress address = new Cart.GuestAddress();
            address.setLine1(request.getAddress().getLine1());
            address.setLine2(request.getAddress().getLine2());
            address.setCity(request.getAddress().getCity());
            address.setState(request.getAddress().getState());
            address.setPostcode(request.getAddress().getPostcode());
            address.setCountry(request.getAddress().getCountry());
            guestContact.setAddress(address);
        }

        return guestContact;
    }

    // Request DTOs

    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContactRequest guestContact;
    }

    @Getter
    @Setter
    public static class GuestContactRequest {
        private String name; // required
        private String email;
        private String phone;
        private GuestAddressRequest address; // required
    }

    @Getter
    @Setter
    public static class GuestAddressRequest {
        private String line1; // required
        private String line2;
        private String city; // required
        private String state;
        private String postcode; // required
        private String country; // required
    }
}
