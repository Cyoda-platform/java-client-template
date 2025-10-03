package com.java_template.application.controller;

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
 * ABOUTME: This file contains the CheckoutController that handles anonymous checkout
 * by attaching guest contact information to carts for order processing.
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
     * Attach guest contact information to cart for anonymous checkout
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<?> attachGuestContact(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Validate cart exists and is in CHECKING_OUT status
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart not found: " + cartId);
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartResponse.entity();
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Cart must be in CHECKING_OUT status. Current status: " + cart.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate required guest contact fields
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Guest contact name is required");
                return ResponseEntity.of(problemDetail).build();
            }

            if (request.getGuestContact().getAddress() == null ||
                request.getGuestContact().getAddress().getLine1() == null ||
                request.getGuestContact().getAddress().getLine1().trim().isEmpty() ||
                request.getGuestContact().getAddress().getCity() == null ||
                request.getGuestContact().getAddress().getCity().trim().isEmpty() ||
                request.getGuestContact().getAddress().getPostcode() == null ||
                request.getGuestContact().getAddress().getPostcode().trim().isEmpty() ||
                request.getGuestContact().getAddress().getCountry() == null ||
                request.getGuestContact().getAddress().getCountry().trim().isEmpty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Guest contact address (line1, city, postcode, country) is required");
                return ResponseEntity.of(problemDetail).build();
            }

            // Attach guest contact to cart
            Cart.GuestContact guestContact = new Cart.GuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            Cart.Address address = new Cart.Address();
            address.setLine1(request.getGuestContact().getAddress().getLine1());
            address.setLine2(request.getGuestContact().getAddress().getLine2());
            address.setCity(request.getGuestContact().getAddress().getCity());
            address.setState(request.getGuestContact().getAddress().getState());
            address.setPostcode(request.getGuestContact().getAddress().getPostcode());
            address.setCountry(request.getGuestContact().getAddress().getCountry());
            guestContact.setAddress(address);

            cart.setGuestContact(guestContact);

            // Update cart (no transition needed - just updating contact info)
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartResponse.metadata().getId(), cart, null);

            logger.info("Guest contact attached to cart {} for checkout", cartId);
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Error attaching guest contact to cart", e);
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
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }
}
