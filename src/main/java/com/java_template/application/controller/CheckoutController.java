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

import java.time.LocalDateTime;

/**
 * ABOUTME: REST controller for Checkout endpoints to attach guest contact information to cart.
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
     * Attach guest contact to cart for checkout
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> checkout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Attach guest contact
            if (request.getGuestContact() != null) {
                Cart.GuestContact contact = new Cart.GuestContact();
                contact.setName(request.getGuestContact().getName());
                contact.setEmail(request.getGuestContact().getEmail());
                contact.setPhone(request.getGuestContact().getPhone());

                if (request.getGuestContact().getAddress() != null) {
                    Cart.Address address = new Cart.Address();
                    address.setLine1(request.getGuestContact().getAddress().getLine1());
                    address.setCity(request.getGuestContact().getAddress().getCity());
                    address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                    address.setCountry(request.getGuestContact().getAddress().getCountry());
                    contact.setAddress(address);
                }

                cart.setGuestContact(contact);
            }

            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "CHECKOUT");
            logger.info("Checkout completed for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to checkout cart {}: {}", cartId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

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

