package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ABOUTME: REST controller for checkout operations including guest contact information
 * management and cart checkout processing for anonymous users.
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
     * Set guest contact information and checkout cart
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> checkout(
            @PathVariable String cartId,
            @Valid @RequestBody CheckoutRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate cart status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart must be in CHECKING_OUT status to checkout, current status: %s", cart.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Set guest contact information
            setGuestContact(cart, request.getGuestContact());

            // Process checkout
            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");
            logger.info("Cart {} checked out successfully", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to checkout cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to checkout cart '%s': %s", cartId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Set guest contact information on cart
     */
    private void setGuestContact(Cart cart, GuestContactRequest guestContactRequest) {
        Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
        guestContact.setName(guestContactRequest.getName());
        guestContact.setEmail(guestContactRequest.getEmail());
        guestContact.setPhone(guestContactRequest.getPhone());

        // Set address
        Cart.CartAddress address = new Cart.CartAddress();
        AddressRequest addressRequest = guestContactRequest.getAddress();
        address.setLine1(addressRequest.getLine1());
        address.setCity(addressRequest.getCity());
        address.setPostcode(addressRequest.getPostcode());
        address.setCountry(addressRequest.getCountry());

        guestContact.setAddress(address);
        cart.setGuestContact(guestContact);

        logger.debug("Guest contact set for cart: {}, name: {}", cart.getCartId(), guestContact.getName());
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
        private String city;
        private String postcode;
        private String country;
    }
}
