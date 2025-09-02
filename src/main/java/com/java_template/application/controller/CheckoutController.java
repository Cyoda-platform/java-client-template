package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/ui/checkout")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping("/{cartId}")
    public ResponseEntity<Cart> attachGuestContact(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request) {

        logger.info("Attaching guest contact to cart: {}", cartId);

        try {
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

            // Create guest contact object
            Cart.GuestContact guestContact = new Cart.GuestContact();
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

            // Attach guest contact to cart
            cart.setGuestContact(guestContact);

            // Update cart (no transition needed for guest contact update)
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartTechnicalId, cart, null);

            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Error attaching guest contact to cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving cart by cartId: {}", cartId, e);
            return null;
        }
    }

    private UUID getCartTechnicalId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(response -> response.getMetadata().getId()).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving cart technical ID: {}", cartId, e);
            return null;
        }
    }
}
