package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ui/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;

    public CheckoutController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/{cartId}")
    public ResponseEntity<Object> setGuestContact(@PathVariable String cartId, @RequestBody Map<String, Object> request) {
        try {
            logger.info("Setting guest contact for cart: {}", cartId);

            @SuppressWarnings("unchecked")
            Map<String, Object> guestContactData = (Map<String, Object>) request.get("guestContact");

            if (guestContactData == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_REQUEST", "message", "Guest contact information is required"));
            }

            // Find cart
            var cartResponses = entityService.findByField(
                    Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, "cartId", cartId);

            if (cartResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponses.get(0).getData();
            UUID cartEntityId = cartResponses.get(0).getMetadata().getId();

            // Create guest contact object
            Cart.GuestContact guestContact = new Cart.GuestContact();
            guestContact.setName((String) guestContactData.get("name"));
            guestContact.setEmail((String) guestContactData.get("email"));
            guestContact.setPhone((String) guestContactData.get("phone"));

            // Handle address
            @SuppressWarnings("unchecked")
            Map<String, Object> addressData = (Map<String, Object>) guestContactData.get("address");
            if (addressData != null) {
                Cart.Address address = new Cart.Address();
                address.setLine1((String) addressData.get("line1"));
                address.setCity((String) addressData.get("city"));
                address.setPostcode((String) addressData.get("postcode"));
                address.setCountry((String) addressData.get("country"));
                guestContact.setAddress(address);
            }

            // Update cart with guest contact
            cart.setGuestContact(guestContact);

            // Save cart without transition (stays in same state)
            var updatedCartResponse = entityService.update(cartEntityId, cart, null);
            Cart updatedCart = updatedCartResponse.getData();

            Map<String, Object> response = new HashMap<>();
            response.put("cartId", updatedCart.getCartId());
            response.put("guestContact", updatedCart.getGuestContact());
            response.put("message", "Guest contact information saved");

            logger.info("Guest contact set for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error setting guest contact for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "CHECKOUT_ERROR", "message", "Failed to set guest contact: " + e.getMessage()));
        }
    }
}
