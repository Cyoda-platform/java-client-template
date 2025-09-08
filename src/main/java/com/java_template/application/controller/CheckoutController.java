package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CheckoutController - UI-facing REST controller for checkout operations
 * 
 * This controller provides:
 * - Anonymous checkout with guest contact information
 * - Cart conversion to checkout state
 * 
 * Endpoints:
 * - POST /ui/checkout/{cartId} - Submit checkout with guest contact
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
     * Submit checkout with guest contact information
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> submitCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Get cart
            EntityWithMetadata<Cart> cartWithMetadata = getCartByCartId(cartId);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate cart is in CHECKING_OUT state
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT state: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Set guest contact information
            Cart.GuestContact guestContact = new Cart.GuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());
            
            if (request.getGuestContact().getAddress() != null) {
                Cart.Address address = new Cart.Address();
                address.setLine1(request.getGuestContact().getAddress().getLine1());
                address.setLine2(request.getGuestContact().getAddress().getLine2());
                address.setCity(request.getGuestContact().getAddress().getCity());
                address.setState(request.getGuestContact().getAddress().getState());
                address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                address.setCountry(request.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            
            cart.setGuestContact(guestContact);

            // Update cart with CHECKOUT transition
            EntityWithMetadata<Cart> response = entityService.update(
                cartWithMetadata.metadata().getId(), cart, "CHECKOUT");
            
            logger.info("Checkout submitted for cart {} with guest: {}", 
                       cartId, request.getGuestContact().getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private EntityWithMetadata<Cart> getCartByCartId(String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);

            return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
        } catch (Exception e) {
            logger.error("Error finding cart by ID: {}", cartId, e);
            return null;
        }
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
        private String name;
        private String email;
        private String phone;
        private AddressRequest address;
    }

    @Getter
    @Setter
    public static class AddressRequest {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }
}
