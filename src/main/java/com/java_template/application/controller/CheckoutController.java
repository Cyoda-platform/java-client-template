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

import java.time.LocalDateTime;

/**
 * Checkout Controller for OMS
 * Provides endpoints for anonymous checkout process with guest contact handling
 * Maps to /ui/checkout/** endpoints
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
     * Submit checkout information (anonymous)
     * POST /ui/checkout/{cartId}
     * Body: { guestContact: { name, email?, phone?, address: { line1, city, postcode, country } } }
     * Attaches guest contact to cart for order creation
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> submitCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Get cart
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found for checkout: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in correct state for checkout
            String currentState = cartWithMetadata.metadata().getState();
            if (!"checking_out".equals(currentState)) {
                logger.warn("Cart {} not in CHECKING_OUT state for checkout submission (current: {})", 
                           cartId, currentState);
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact information
            if (request.getGuestContact() == null) {
                logger.warn("Guest contact information required for checkout");
                return ResponseEntity.badRequest().build();
            }

            if (!isValidGuestContact(request.getGuestContact())) {
                logger.warn("Invalid guest contact information for checkout");
                return ResponseEntity.badRequest().build();
            }

            // Attach guest contact to cart
            Cart.CartGuestContact cartContact = new Cart.CartGuestContact();
            cartContact.setName(request.getGuestContact().getName());
            cartContact.setEmail(request.getGuestContact().getEmail());
            cartContact.setPhone(request.getGuestContact().getPhone());

            if (request.getGuestContact().getAddress() != null) {
                Cart.CartAddress cartAddress = new Cart.CartAddress();
                cartAddress.setLine1(request.getGuestContact().getAddress().getLine1());
                cartAddress.setCity(request.getGuestContact().getAddress().getCity());
                cartAddress.setPostcode(request.getGuestContact().getAddress().getPostcode());
                cartAddress.setCountry(request.getGuestContact().getAddress().getCountry());
                cartContact.setAddress(cartAddress);
            }

            cart.setGuestContact(cartContact);
            cart.setUpdatedAt(LocalDateTime.now());

            // Update cart (no transition - stay in CHECKING_OUT state)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Checkout information submitted for cart {} with guest: {}", 
                       cartId, request.getGuestContact().getName());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get checkout information for cart
     * GET /ui/checkout/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<CheckoutInfoResponse> getCheckoutInfo(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            CheckoutInfoResponse response = new CheckoutInfoResponse();
            response.setCartId(cart.getCartId());
            response.setTotalItems(cart.getTotalItems());
            response.setGrandTotal(cart.getGrandTotal());
            response.setStatus(cartWithMetadata.metadata().getState().toUpperCase());

            if (cart.getGuestContact() != null) {
                CheckoutGuestContact guestContact = new CheckoutGuestContact();
                guestContact.setName(cart.getGuestContact().getName());
                guestContact.setEmail(cart.getGuestContact().getEmail());
                guestContact.setPhone(cart.getGuestContact().getPhone());

                if (cart.getGuestContact().getAddress() != null) {
                    CheckoutAddress address = new CheckoutAddress();
                    address.setLine1(cart.getGuestContact().getAddress().getLine1());
                    address.setCity(cart.getGuestContact().getAddress().getCity());
                    address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                    address.setCountry(cart.getGuestContact().getAddress().getCountry());
                    guestContact.setAddress(address);
                }

                response.setGuestContact(guestContact);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting checkout info for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private boolean isValidGuestContact(CheckoutGuestContact guestContact) {
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return false;
        }

        if (guestContact.getAddress() == null) {
            return false;
        }

        CheckoutAddress address = guestContact.getAddress();
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class CheckoutRequest {
        private CheckoutGuestContact guestContact;
    }

    @Getter
    @Setter
    public static class CheckoutGuestContact {
        private String name;
        private String email;
        private String phone;
        private CheckoutAddress address;
    }

    @Getter
    @Setter
    public static class CheckoutAddress {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }

    @Getter
    @Setter
    public static class CheckoutInfoResponse {
        private String cartId;
        private Integer totalItems;
        private Double grandTotal;
        private String status;
        private CheckoutGuestContact guestContact;
    }
}
