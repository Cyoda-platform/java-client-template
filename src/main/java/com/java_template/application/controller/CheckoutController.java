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
 * Checkout controller for anonymous checkout process
 * Handles guest contact information attachment to cart
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
     * Process anonymous checkout with guest contact information
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> processCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in CHECKING_OUT status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Validate address
            CheckoutRequest.GuestContact guestContact = request.getGuestContact();
            if (guestContact.getAddress() == null ||
                guestContact.getAddress().getLine1() == null ||
                guestContact.getAddress().getLine1().trim().isEmpty() ||
                guestContact.getAddress().getCity() == null ||
                guestContact.getAddress().getCity().trim().isEmpty() ||
                guestContact.getAddress().getPostcode() == null ||
                guestContact.getAddress().getPostcode().trim().isEmpty() ||
                guestContact.getAddress().getCountry() == null ||
                guestContact.getAddress().getCountry().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Convert request to cart guest contact
            Cart.GuestContact cartGuestContact = new Cart.GuestContact();
            cartGuestContact.setName(guestContact.getName());
            cartGuestContact.setEmail(guestContact.getEmail());
            cartGuestContact.setPhone(guestContact.getPhone());

            Cart.Address cartAddress = new Cart.Address();
            cartAddress.setLine1(guestContact.getAddress().getLine1());
            cartAddress.setLine2(guestContact.getAddress().getLine2());
            cartAddress.setCity(guestContact.getAddress().getCity());
            cartAddress.setState(guestContact.getAddress().getState());
            cartAddress.setPostcode(guestContact.getAddress().getPostcode());
            cartAddress.setCountry(guestContact.getAddress().getCountry());
            cartGuestContact.setAddress(cartAddress);

            // Attach guest contact to cart
            cart.setGuestContact(cartGuestContact);

            // Update cart with checkout transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "checkout");

            logger.info("Checkout processed for cart {} with guest: {}", 
                       cartId, guestContact.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContact guestContact;

        @Getter
        @Setter
        public static class GuestContact {
            private String name;
            private String email;
            private String phone;
            private Address address;

            @Getter
            @Setter
            public static class Address {
                private String line1;
                private String line2;
                private String city;
                private String state;
                private String postcode;
                private String country;
            }
        }
    }
}
