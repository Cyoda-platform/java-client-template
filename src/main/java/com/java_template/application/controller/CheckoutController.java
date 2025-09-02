package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ui/checkout")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;

    public CheckoutController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/{cartId}")
    public ResponseEntity<Map<String, Object>> checkout(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request) {
        
        try {
            logger.info("Processing checkout for cart: {}", cartId);

            // Extract guest contact from request
            @SuppressWarnings("unchecked")
            Map<String, Object> guestContactData = (Map<String, Object>) request.get("guestContact");
            
            if (guestContactData == null) {
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.notFound().build();
            }

            // Get cart entity ID for update
            UUID cartEntityId = getCartEntityId(cartId);
            if (cartEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            // Convert guest contact data to Cart.GuestContact
            Cart.GuestContact guestContact = convertToGuestContact(guestContactData);
            cart.setGuestContact(guestContact);

            // Update cart with checkout transition
            EntityResponse<Cart> updatedCart = entityService.update(cartEntityId, cart, "checkout");

            Map<String, Object> response = convertToCheckoutResponse(updatedCart.getData(), updatedCart.getMetadata().getState());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing checkout for cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, 
                Cart.ENTITY_NAME, 
                Cart.ENTITY_VERSION, 
                condition, 
                true
            );

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching cart by ID: {}", cartId, e);
            return null;
        }
    }

    private UUID getCartEntityId(String cartId) {
        // TODO: Implement proper entity ID lookup
        return null;
    }

    private Cart.GuestContact convertToGuestContact(Map<String, Object> guestContactData) {
        Cart.GuestContact guestContact = new Cart.GuestContact();
        guestContact.setName((String) guestContactData.get("name"));
        guestContact.setEmail((String) guestContactData.get("email"));
        guestContact.setPhone((String) guestContactData.get("phone"));

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
    }

    private Map<String, Object> convertToCheckoutResponse(Cart cart, String state) {
        Map<String, Object> response = new HashMap<>();
        response.put("cartId", cart.getCartId());
        response.put("state", state);
        response.put("guestContact", convertGuestContactToMap(cart.getGuestContact()));
        response.put("grandTotal", cart.getGrandTotal());
        return response;
    }

    private Map<String, Object> convertGuestContactToMap(Cart.GuestContact guestContact) {
        if (guestContact == null) {
            return null;
        }

        Map<String, Object> contactMap = new HashMap<>();
        contactMap.put("name", guestContact.getName());
        contactMap.put("email", guestContact.getEmail());
        contactMap.put("phone", guestContact.getPhone());

        if (guestContact.getAddress() != null) {
            Map<String, Object> addressMap = new HashMap<>();
            addressMap.put("line1", guestContact.getAddress().getLine1());
            addressMap.put("city", guestContact.getAddress().getCity());
            addressMap.put("postcode", guestContact.getAddress().getPostcode());
            addressMap.put("country", guestContact.getAddress().getCountry());
            contactMap.put("address", addressMap);
        }

        return contactMap;
    }
}
