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
 * CheckoutController - REST endpoints for checkout operations
 * 
 * Provides UI-facing APIs for anonymous checkout with guest contact information.
 * Base path: /ui/checkout
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
     * POST /ui/checkout/{cartId} - Set guest contact information
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> setGuestContact(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate guest contact information
            if (request.getGuestContact() == null) {
                logger.error("Guest contact information is required");
                return ResponseEntity.badRequest().build();
            }

            // Convert DTO to entity
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

            // Set guest contact on cart
            cart.setGuestContact(guestContact);

            // Update cart without transition (stays in same state)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);
            
            logger.info("Guest contact set for cart {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error setting guest contact for cart", e);
            return ResponseEntity.badRequest().build();
        }
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
        private String city;
        private String postcode;
        private String country;
    }
}
