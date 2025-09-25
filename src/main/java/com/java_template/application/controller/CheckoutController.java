package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Checkout controller for anonymous checkout with guest contact information.
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
     * Submit guest contact information for checkout
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> submitGuestContact(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {

        logger.info("Submitting guest contact for cart: {}", cartId);

        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

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
            if (request.getGuestContact() == null || 
                !isValidGuestContact(request.getGuestContact())) {
                logger.warn("Invalid guest contact information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Set guest contact information
            Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            if (request.getGuestContact().getAddress() != null) {
                Cart.CartAddress address = new Cart.CartAddress();
                address.setLine1(request.getGuestContact().getAddress().getLine1());
                address.setCity(request.getGuestContact().getAddress().getCity());
                address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                address.setCountry(request.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }

            cart.setGuestContact(guestContact);
            cart.setUpdatedAt(LocalDateTime.now());

            // Update cart (no transition - stay in CHECKING_OUT)
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Guest contact submitted for cart: {} - Name: {}", 
                       cartId, guestContact.getName());

            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error submitting guest contact for cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Validate guest contact information
     */
    private boolean isValidGuestContact(GuestContactDto guestContact) {
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return false;
        }

        if (guestContact.getAddress() == null) {
            return false;
        }

        AddressDto address = guestContact.getAddress();
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    @Data
    public static class CheckoutRequest {
        private GuestContactDto guestContact;
    }

    @Data
    public static class GuestContactDto {
        private String name;
        private String email;
        private String phone;
        private AddressDto address;
    }

    @Data
    public static class AddressDto {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
