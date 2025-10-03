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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ABOUTME: Checkout controller providing REST APIs for anonymous checkout process
 * including guest contact information collection.
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
     * Submit checkout with guest contact information
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<?> submitCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in CHECKING_OUT status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Cart must be in CHECKING_OUT status. Current status: " + cart.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate guest contact information
            if (request.getGuestContact() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Guest contact information is required");
                return ResponseEntity.of(problemDetail).build();
            }

            if (request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Guest name is required");
                return ResponseEntity.of(problemDetail).build();
            }

            if (request.getGuestContact().getAddress() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Guest address is required");
                return ResponseEntity.of(problemDetail).build();
            }

            GuestAddress address = request.getGuestContact().getAddress();
            if (address.getLine1() == null || address.getLine1().trim().isEmpty() ||
                address.getCity() == null || address.getCity().trim().isEmpty() ||
                address.getPostcode() == null || address.getPostcode().trim().isEmpty() ||
                address.getCountry() == null || address.getCountry().trim().isEmpty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Complete address information is required");
                return ResponseEntity.of(problemDetail).build();
            }

            // Convert request to cart guest contact
            Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            Cart.CartGuestAddress cartAddress = new Cart.CartGuestAddress();
            cartAddress.setLine1(address.getLine1());
            cartAddress.setCity(address.getCity());
            cartAddress.setPostcode(address.getPostcode());
            cartAddress.setCountry(address.getCountry());
            guestContact.setAddress(cartAddress);

            // Update cart with guest contact information
            cart.setGuestContact(guestContact);

            // Update cart to CONVERTED status (checkout complete)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "checkout");
            
            logger.info("Checkout completed for cart {} with guest: {}", 
                       cartId, guestContact.getName());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing checkout", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContact guestContact;
    }

    @Getter
    @Setter
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private GuestAddress address;
    }

    @Getter
    @Setter
    public static class GuestAddress {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
