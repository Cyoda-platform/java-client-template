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
 * Handles guest contact information and address collection
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
            // Get existing cart
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();
            
            // Validate cart is in checking out status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Set guest contact information
            Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            // Set address
            if (request.getGuestContact().getAddress() != null) {
                Cart.CartAddress address = new Cart.CartAddress();
                address.setLine1(request.getGuestContact().getAddress().getLine1());
                address.setCity(request.getGuestContact().getAddress().getCity());
                address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                address.setCountry(request.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }

            cart.setGuestContact(guestContact);

            // Complete checkout
            EntityWithMetadata<Cart> response = entityService.update(cartResponse.metadata().getId(), cart, "checkout");
            logger.info("Checkout completed for cart {} with guest: {}", cartId, guestContact.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request DTOs for checkout
     */
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
