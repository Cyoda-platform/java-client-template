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
 * Checkout Controller - REST API for checkout process
 * 
 * Provides endpoints for:
 * - Setting guest contact information for anonymous checkout
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
     * Set guest contact information for checkout
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> setGuestContact(
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
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} not in CHECKING_OUT status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty() ||
                request.getGuestContact().getAddress() == null ||
                !isValidAddress(request.getGuestContact().getAddress())) {
                logger.warn("Invalid guest contact information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Set guest contact
            Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());
            
            Cart.CartAddress address = new Cart.CartAddress();
            address.setLine1(request.getGuestContact().getAddress().getLine1());
            address.setCity(request.getGuestContact().getAddress().getCity());
            address.setPostcode(request.getGuestContact().getAddress().getPostcode());
            address.setCountry(request.getGuestContact().getAddress().getCountry());
            guestContact.setAddress(address);
            
            cart.setGuestContact(guestContact);
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);
            
            logger.info("Guest contact set for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error setting guest contact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate address information
     */
    private boolean isValidAddress(CheckoutAddress address) {
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    // Request DTOs

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
}
