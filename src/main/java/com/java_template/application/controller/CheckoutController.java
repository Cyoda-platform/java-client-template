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
import java.util.UUID;

/**
 * ABOUTME: REST controller for checkout operations (guest contact attachment).
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
     * Attach guest contact to cart and proceed with checkout
     * POST /ui/checkout/{cartId}
     * Body: { "guestContact": { "name": "...", "email": "...", "phone": "...", "address": {...} } }
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> checkout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            UUID cartId_uuid = cartWithMetadata.metadata().getId();

            // Attach guest contact
            if (request.getGuestContact() != null) {
                Cart.GuestContact guestContact = new Cart.GuestContact();
                guestContact.setName(request.getGuestContact().getName());
                guestContact.setEmail(request.getGuestContact().getEmail());
                guestContact.setPhone(request.getGuestContact().getPhone());
                
                if (request.getGuestContact().getAddress() != null) {
                    Cart.Address address = new Cart.Address();
                    address.setLine1(request.getGuestContact().getAddress().getLine1());
                    address.setCity(request.getGuestContact().getAddress().getCity());
                    address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                    address.setCountry(request.getGuestContact().getAddress().getCountry());
                    guestContact.setAddress(address);
                }
                
                cart.setGuestContact(guestContact);
            }

            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(cartId_uuid, cart, "checkout");
            logger.info("Cart {} checked out with guest contact", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to checkout cart: %s", e.getMessage())
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

