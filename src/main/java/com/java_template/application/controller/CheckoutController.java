package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Checkout Controller - Handles anonymous checkout process for OMS
 * 
 * Provides endpoints for:
 * - Anonymous checkout with guest contact information
 * - Cart conversion to checkout state
 */
@RestController
@RequestMapping("/ui/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CheckoutController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Process anonymous checkout
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> processCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            logger.info("Processing checkout for cart: {}", cartId);

            // Validate request
            if (request.getGuestContact() == null) {
                logger.warn("Missing guest contact information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            if (!isValidGuestContact(request.getGuestContact())) {
                logger.warn("Invalid guest contact information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

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
                logger.warn("Cart not in CHECKING_OUT status: {} - Status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.warn("Cannot checkout empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Attach guest contact information to cart
            cart.setGuestContact(mapToCartGuestContact(request.getGuestContact()));
            cart.setUpdatedAt(LocalDateTime.now());

            // Update cart with guest contact information
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Checkout processed for cart: {} with guest: {}", 
                       cartId, request.getGuestContact().getName());
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Error processing checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate guest contact information
     */
    private boolean isValidGuestContact(GuestContactRequest guestContact) {
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return false;
        }

        if (guestContact.getAddress() == null) {
            return false;
        }

        GuestAddressRequest address = guestContact.getAddress();
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    /**
     * Map request DTO to cart entity guest contact
     */
    private Cart.GuestContact mapToCartGuestContact(GuestContactRequest request) {
        Cart.GuestContact guestContact = new Cart.GuestContact();
        guestContact.setName(request.getName());
        guestContact.setEmail(request.getEmail());
        guestContact.setPhone(request.getPhone());

        if (request.getAddress() != null) {
            Cart.GuestAddress address = new Cart.GuestAddress();
            address.setLine1(request.getAddress().getLine1());
            address.setCity(request.getAddress().getCity());
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
