package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ui/checkout")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/{cartId}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> addGuestContact(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request) {

        logger.info("Adding guest contact to cart {} with request: {}", cartId, request);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> guestContactData = (Map<String, Object>) request.get("guestContact");

            if (guestContactData == null) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            // Validate required fields
            String name = (String) guestContactData.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> addressData = (Map<String, Object>) guestContactData.get("address");

            if (name == null || name.trim().isEmpty()) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            if (addressData == null) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            String line1 = (String) addressData.get("line1");
            String city = (String) addressData.get("city");
            String postcode = (String) addressData.get("postcode");
            String country = (String) addressData.get("country");

            if (line1 == null || line1.trim().isEmpty() ||
                city == null || city.trim().isEmpty() ||
                postcode == null || postcode.trim().isEmpty() ||
                country == null || country.trim().isEmpty()) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            // Get cart and update with guest contact
            return getCartByCartId(cartId).thenCompose(cartOptional -> {
                if (cartOptional.isEmpty()) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                }

                Cart cart = cartOptional.get();

                // Create guest contact
                Cart.GuestContact guestContact = new Cart.GuestContact();
                guestContact.setName(name);
                guestContact.setEmail((String) guestContactData.get("email"));
                guestContact.setPhone((String) guestContactData.get("phone"));

                Cart.GuestAddress address = new Cart.GuestAddress();
                address.setLine1(line1);
                address.setCity(city);
                address.setPostcode(postcode);
                address.setCountry(country);
                guestContact.setAddress(address);

                cart.setGuestContact(guestContact);

                // Update cart
                return getCartEntityId(cartId).thenCompose(entityId -> {
                    if (entityId == null) {
                        return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                    }

                    return entityService.updateItem(entityId, cart).thenApply(updatedEntityId -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("cartId", cartId);
                        response.put("state", "CHECKING_OUT");
                        response.put("guestContact", guestContact);
                        response.put("totalItems", cart.getTotalItems());
                        response.put("grandTotal", cart.getGrandTotal());

                        return ResponseEntity.ok(response);
                    });
                });
            }).exceptionally(throwable -> {
                logger.error("Error adding guest contact: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in addGuestContact: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    private CompletableFuture<java.util.Optional<Cart>> getCartByCartId(String cartId) {
        Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

        return entityService.getFirstItemByCondition(
            Cart.ENTITY_NAME,
            Cart.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                try {
                    Cart cart = objectMapper.convertValue(optionalPayload.get().getData(), Cart.class);
                    return java.util.Optional.of(cart);
                } catch (Exception e) {
                    logger.error("Error converting cart data: {}", e.getMessage(), e);
                    return java.util.Optional.empty();
                }
            }
            return java.util.Optional.empty();
        });
    }

    private CompletableFuture<UUID> getCartEntityId(String cartId) {
        Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

        return entityService.getFirstItemByCondition(
            Cart.ENTITY_NAME,
            Cart.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                return optionalPayload.get().getMetadata().getId();
            }
            return null;
        });
    }
}