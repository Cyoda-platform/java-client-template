package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ABOUTME: Checkout controller providing REST endpoints for anonymous checkout
 * process with guest contact information collection.
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
     * Submit checkout information for anonymous guest
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> submitCheckout(
            @PathVariable String cartId,
            @Valid @RequestBody CheckoutRequest request) {
        try {
            // Validate cart exists and is in CHECKING_OUT status
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart not found with ID: %s", cartId)
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();

            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart must be in CHECKING_OUT status. Current status: %s", cart.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate required fields
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Guest contact name is required"
                );
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
                    "Complete address information is required (line1, city, postcode, country)"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Update cart with guest contact information
            Cart.GuestContact guestContact = new Cart.GuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            Cart.GuestAddress address = new Cart.GuestAddress();
            address.setLine1(request.getGuestContact().getAddress().getLine1());
            address.setCity(request.getGuestContact().getAddress().getCity());
            address.setPostcode(request.getGuestContact().getAddress().getPostcode());
            address.setCountry(request.getGuestContact().getAddress().getCountry());
            guestContact.setAddress(address);

            cart.setGuestContact(guestContact);

            // Update cart (no transition needed, just data update)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Checkout information submitted for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to submit checkout for cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to submit checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get checkout information for cart
     * GET /ui/checkout/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<CheckoutResponse> getCheckoutInfo(@PathVariable String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            CheckoutResponse response = new CheckoutResponse();
            response.setCartId(cart.getCartId());
            response.setStatus(cart.getStatus());
            response.setTotalItems(cart.getTotalItems());
            response.setGrandTotal(cart.getGrandTotal());

            if (cart.getGuestContact() != null) {
                CheckoutGuestContact guestContact = new CheckoutGuestContact();
                guestContact.setName(cart.getGuestContact().getName());
                guestContact.setEmail(cart.getGuestContact().getEmail());
                guestContact.setPhone(cart.getGuestContact().getPhone());

                if (cart.getGuestContact().getAddress() != null) {
                    CheckoutGuestAddress address = new CheckoutGuestAddress();
                    address.setLine1(cart.getGuestContact().getAddress().getLine1());
                    address.setCity(cart.getGuestContact().getAddress().getCity());
                    address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                    address.setCountry(cart.getGuestContact().getAddress().getCountry());
                    guestContact.setAddress(address);
                }

                response.setGuestContact(guestContact);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get checkout info for cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to get checkout info: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Data
    public static class CheckoutRequest {
        private CheckoutGuestContact guestContact;
    }

    @Data
    public static class CheckoutGuestContact {
        private String name;
        private String email;
        private String phone;
        private CheckoutGuestAddress address;
    }

    @Data
    public static class CheckoutGuestAddress {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }

    @Data
    public static class CheckoutResponse {
        private String cartId;
        private String status;
        private Integer totalItems;
        private Double grandTotal;
        private CheckoutGuestContact guestContact;
    }
}
