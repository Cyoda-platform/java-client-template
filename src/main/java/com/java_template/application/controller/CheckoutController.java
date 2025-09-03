package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
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
    public ResponseEntity<EntityResponse<Cart>> completeCheckout(
            @PathVariable String cartId, 
            @RequestBody Map<String, Object> request) {
        try {
            logger.info("Completing checkout for cart: {}", cartId);

            // Extract guest contact from request
            @SuppressWarnings("unchecked")
            Map<String, Object> guestContactData = (Map<String, Object>) request.get("guestContact");
            
            if (guestContactData == null) {
                logger.warn("Guest contact is required for checkout");
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Build guest contact from request data
            Cart.GuestContact guestContact = buildGuestContact(guestContactData);
            if (guestContact == null || !guestContact.isValid()) {
                logger.warn("Invalid guest contact data");
                return ResponseEntity.badRequest().build();
            }

            // Set guest contact on cart
            cart.setGuestContact(guestContact);

            // Update cart with checkout transition
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            EntityResponse<Cart> response = entityService.update(cartTechnicalId, cart, "checkout");
            
            logger.info("Checkout completed for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error completing checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting cart by cartId: {}", cartId, e);
            return null;
        }
    }

    private UUID getCartTechnicalId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(response -> response.getMetadata().getId()).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting cart technical ID: {}", cartId, e);
            return null;
        }
    }

    private Cart.GuestContact buildGuestContact(Map<String, Object> guestContactData) {
        try {
            Cart.GuestContact guestContact = new Cart.GuestContact();
            
            // Extract basic contact info
            guestContact.setName((String) guestContactData.get("name"));
            guestContact.setEmail((String) guestContactData.get("email"));
            guestContact.setPhone((String) guestContactData.get("phone"));
            
            // Extract address
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
            
            return guestContact;
            
        } catch (Exception e) {
            logger.error("Error building guest contact from data", e);
            return null;
        }
    }
}
