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
 * Checkout Controller - Anonymous checkout functionality
 * 
 * Provides endpoints for:
 * - Anonymous checkout with guest contact information
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
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

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

            // Attach guest contact information
            Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            // Attach address
            Cart.CartAddress address = new Cart.CartAddress();
            address.setLine1(request.getGuestContact().getAddress().getLine1());
            address.setCity(request.getGuestContact().getAddress().getCity());
            address.setPostcode(request.getGuestContact().getAddress().getPostcode());
            address.setCountry(request.getGuestContact().getAddress().getCountry());
            guestContact.setAddress(address);

            cart.setGuestContact(guestContact);

            // Update cart with checkout transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "checkout");

            logger.info("Checkout completed for cart {} with guest: {}", 
                       cartId, guestContact.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContactDto guestContact;
    }

    @Getter
    @Setter
    public static class GuestContactDto {
        private String name;
        private String email;
        private String phone;
        private AddressDto address;
    }

    @Getter
    @Setter
    public static class AddressDto {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
