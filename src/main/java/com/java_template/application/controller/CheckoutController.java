package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Checkout Controller - Manages anonymous checkout process
 * 
 * Endpoints:
 * - POST /ui/checkout/{cartId} - Add guest contact information to cart
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
     * Add guest contact information to cart
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> addGuestContact(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Get cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }
            
            Cart cart = cartResponse.entity();

            // Validate cart is in CHECKING_OUT state
            String currentState = cartResponse.getState();
            if (!"CHECKING_OUT".equals(currentState)) {
                logger.error("Cart {} is not in CHECKING_OUT state, current state: {}", cartId, currentState);
                return ResponseEntity.badRequest().build();
            }
            
            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.error("Cannot checkout empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }
            
            // Validate guest contact
            if (request.getGuestContact() == null || !isValidGuestContact(request.getGuestContact())) {
                logger.error("Invalid guest contact information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }
            
            // Set guest contact information
            cart.setGuestContact(mapToCartGuestContact(request.getGuestContact()));
            cart.setUpdatedAt(LocalDateTime.now());
            
            // Update cart without transition (stay in CHECKING_OUT state)
            EntityWithMetadata<Cart> response = entityService.update(
                cartResponse.getId(), cart, null);
            
            logger.info("Guest contact added to cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding guest contact to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate guest contact information
     */
    private boolean isValidGuestContact(GuestContactDto guestContact) {
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return false;
        }
        
        if (guestContact.getAddress() == null) {
            return false;
        }
        
        AddressDto address = guestContact.getAddress();
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    /**
     * Map DTO to Cart entity guest contact
     */
    private Cart.CartGuestContact mapToCartGuestContact(GuestContactDto dto) {
        Cart.CartGuestContact guestContact = new Cart.CartGuestContact();
        guestContact.setName(dto.getName());
        guestContact.setEmail(dto.getEmail());
        guestContact.setPhone(dto.getPhone());
        
        if (dto.getAddress() != null) {
            Cart.CartAddress address = new Cart.CartAddress();
            address.setLine1(dto.getAddress().getLine1());
            address.setCity(dto.getAddress().getCity());
            address.setPostcode(dto.getAddress().getPostcode());
            address.setCountry(dto.getAddress().getCountry());
            guestContact.setAddress(address);
        }
        
        return guestContact;
    }

    // Request DTOs

    public static class CheckoutRequest {
        private GuestContactDto guestContact;

        public GuestContactDto getGuestContact() { return guestContact; }
        public void setGuestContact(GuestContactDto guestContact) { this.guestContact = guestContact; }
    }

    public static class GuestContactDto {
        private String name;
        private String email;
        private String phone;
        private AddressDto address;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        
        public AddressDto getAddress() { return address; }
        public void setAddress(AddressDto address) { this.address = address; }
    }

    public static class AddressDto {
        private String line1;
        private String city;
        private String postcode;
        private String country;

        public String getLine1() { return line1; }
        public void setLine1(String line1) { this.line1 = line1; }
        
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        
        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }
        
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
    }
}
