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

/**
 * Checkout controller for anonymous checkout flow in OMS
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
     * Process anonymous checkout with guest contact information
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> processCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in checkout state
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT state: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Attach guest contact information to cart
            attachGuestContactToCart(cart, request.getGuestContact());

            // Update cart with checkout transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "checkout");

            logger.info("Checkout processed for cart {} with guest contact: {}", 
                       cartId, request.getGuestContact().getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Attach guest contact information to cart
     */
    private void attachGuestContactToCart(Cart cart, GuestContactDto guestContactDto) {
        Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
        guestContact.setName(guestContactDto.getName());
        guestContact.setEmail(guestContactDto.getEmail());
        guestContact.setPhone(guestContactDto.getPhone());

        if (guestContactDto.getAddress() != null) {
            Cart.CartAddress address = new Cart.CartAddress();
            address.setLine1(guestContactDto.getAddress().getLine1());
            address.setLine2(guestContactDto.getAddress().getLine2());
            address.setCity(guestContactDto.getAddress().getCity());
            address.setState(guestContactDto.getAddress().getState());
            address.setPostcode(guestContactDto.getAddress().getPostcode());
            address.setCountry(guestContactDto.getAddress().getCountry());
            guestContact.setAddress(address);
        }

        cart.setGuestContact(guestContact);
    }

    // Request DTOs

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
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }
}
