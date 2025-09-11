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

import java.time.LocalDateTime;

/**
 * CheckoutController - Manages anonymous checkout process.
 * 
 * Endpoints:
 * - POST /ui/checkout/{cartId} - Attach guest contact information to cart
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
     * Attach guest contact information to cart
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> attachGuestContact(
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

            // Convert request DTO to cart guest contact
            Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            if (request.getGuestContact().getAddress() != null) {
                Cart.CartAddress address = new Cart.CartAddress();
                address.setLine1(request.getGuestContact().getAddress().getLine1());
                address.setCity(request.getGuestContact().getAddress().getCity());
                address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                address.setCountry(request.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }

            cart.setGuestContact(guestContact);
            cart.setUpdatedAt(LocalDateTime.now());

            // Update cart without transition (stay in same state)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);
            
            logger.info("Guest contact attached to cart {}: {}", cartId, guestContact.getName());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error attaching guest contact to cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request DTO for checkout
     */
    @Data
    public static class CheckoutRequest {
        private GuestContactDto guestContact;
    }

    /**
     * Guest contact DTO
     */
    @Data
    public static class GuestContactDto {
        private String name;
        private String email;
        private String phone;
        private AddressDto address;
    }

    /**
     * Address DTO
     */
    @Data
    public static class AddressDto {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
