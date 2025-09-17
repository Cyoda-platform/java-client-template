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
 * CheckoutController - REST endpoints for checkout process
 * 
 * Handles anonymous checkout by attaching guest contact information
 * to the cart before final checkout transition.
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
     * Attach guest contact and complete checkout
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> checkout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();

            // Validate cart is in CHECKING_OUT state
            String currentState = cartResponse.metadata().getState();
            if (!"checking_out".equals(currentState)) {
                logger.error("Cart {} is not in checking_out state. Current state: {}", cartId, currentState);
                return ResponseEntity.badRequest().build();
            }

            // Attach guest contact information
            Cart.GuestContact guestContact = new Cart.GuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            // Attach address
            Cart.GuestAddress address = new Cart.GuestAddress();
            address.setLine1(request.getGuestContact().getAddress().getLine1());
            address.setCity(request.getGuestContact().getAddress().getCity());
            address.setPostcode(request.getGuestContact().getAddress().getPostcode());
            address.setCountry(request.getGuestContact().getAddress().getCountry());
            guestContact.setAddress(address);

            cart.setGuestContact(guestContact);

            // Complete checkout with CHECKOUT transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, "CHECKOUT");
            
            logger.info("Checkout completed for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error completing checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request DTOs
     */
    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContactRequest guestContact;
    }

    @Getter
    @Setter
    public static class GuestContactRequest {
        private String name;
        private String email;
        private String phone;
        private GuestAddressRequest address;
    }

    @Getter
    @Setter
    public static class GuestAddressRequest {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
