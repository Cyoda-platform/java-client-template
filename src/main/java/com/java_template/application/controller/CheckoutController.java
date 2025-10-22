package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ABOUTME: Checkout controller handling anonymous checkout process with
 * guest contact information collection and cart conversion.
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
     * Process anonymous checkout
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> processCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
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
            
            // Validate cart is in checkout state
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart must be in CHECKING_OUT status to process checkout"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Attach guest contact information
            Cart.GuestContact guestContact = new Cart.GuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());
            
            if (request.getGuestContact().getAddress() != null) {
                Cart.GuestAddress address = new Cart.GuestAddress();
                address.setLine1(request.getGuestContact().getAddress().getLine1());
                address.setCity(request.getGuestContact().getAddress().getCity());
                address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                address.setCountry(request.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            
            cart.setGuestContact(guestContact);
            cart.setStatus("CONVERTED");

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "checkout");
            
            logger.info("Processed checkout for cart {} with guest {}", 
                       cartId, request.getGuestContact().getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to process checkout for cart {}: {}", cartId, e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to process checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Data
    public static class CheckoutRequest {
        private GuestContactRequest guestContact;
    }

    @Data
    public static class GuestContactRequest {
        private String name;
        private String email;
        private String phone;
        private GuestAddressRequest address;
    }

    @Data
    public static class GuestAddressRequest {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
