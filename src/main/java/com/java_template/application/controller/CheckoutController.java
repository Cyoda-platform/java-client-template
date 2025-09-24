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
 * CheckoutController - REST endpoints for anonymous checkout
 * 
 * Provides endpoints for:
 * - Anonymous checkout with guest contact information
 * - Cart conversion to order-ready state
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
     * Anonymous checkout - attach guest contact to cart
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
                logger.warn("Cart not found for checkout: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();

            // Validate cart is in CHECKING_OUT status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.warn("Cannot checkout empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                logger.warn("Guest contact name is required for checkout");
                return ResponseEntity.badRequest().build();
            }

            // Validate address
            CheckoutRequest.GuestContact guestContact = request.getGuestContact();
            if (guestContact.getAddress() == null ||
                guestContact.getAddress().getLine1() == null ||
                guestContact.getAddress().getCity() == null ||
                guestContact.getAddress().getPostcode() == null ||
                guestContact.getAddress().getCountry() == null) {
                logger.warn("Complete address is required for checkout");
                return ResponseEntity.badRequest().build();
            }

            // Attach guest contact to cart
            attachGuestContactToCart(cart, request.getGuestContact());

            // Convert cart to CONVERTED status
            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, "checkout");

            logger.info("Checkout completed for cart {} with guest: {}", 
                       cartId, guestContact.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Attach guest contact information to cart
     */
    private void attachGuestContactToCart(Cart cart, CheckoutRequest.GuestContact guestContactRequest) {
        Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
        guestContact.setName(guestContactRequest.getName());
        guestContact.setEmail(guestContactRequest.getEmail());
        guestContact.setPhone(guestContactRequest.getPhone());

        if (guestContactRequest.getAddress() != null) {
            Cart.CartGuestAddress address = new Cart.CartGuestAddress();
            address.setLine1(guestContactRequest.getAddress().getLine1());
            address.setLine2(guestContactRequest.getAddress().getLine2());
            address.setCity(guestContactRequest.getAddress().getCity());
            address.setState(guestContactRequest.getAddress().getState());
            address.setPostcode(guestContactRequest.getAddress().getPostcode());
            address.setCountry(guestContactRequest.getAddress().getCountry());
            guestContact.setAddress(address);
        }

        cart.setGuestContact(guestContact);
    }

    /**
     * Request DTO for checkout
     */
    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContact guestContact;

        @Getter
        @Setter
        public static class GuestContact {
            private String name; // required
            private String email;
            private String phone;
            private GuestAddress address; // required

            @Getter
            @Setter
            public static class GuestAddress {
                private String line1; // required
                private String line2;
                private String city; // required
                private String state;
                private String postcode; // required
                private String country; // required
            }
        }
    }
}
