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
@RequestMapping("/ui/cart")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createCart(@RequestBody Map<String, Object> request) {
        logger.info("Creating cart with request: {}", request);

        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            if (sku == null || qty == null || qty <= 0) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            // Create cart entity with input data
            Cart cart = new Cart();

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("sku", sku);
            inputData.put("qty", qty);

            // Add cart with CREATE_ON_FIRST_ADD transition
            return entityService.addItem(Cart.ENTITY_NAME, Cart.ENTITY_VERSION, cart)
                .thenApply(cartId -> {
                    try {
                        // Get the created cart
                        return getCartResponse(cartId.toString());
                    } catch (Exception e) {
                        logger.error("Error getting created cart: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                }).exceptionally(throwable -> {
                    logger.error("Error creating cart: {}", throwable.getMessage(), throwable);
                    return ResponseEntity.internalServerError().build();
                });

        } catch (Exception e) {
            logger.error("Error in createCart: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @GetMapping("/{cartId}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getCart(@PathVariable String cartId) {
        logger.info("Getting cart: {}", cartId);

        try {
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
                        String state = optionalPayload.get().getMetadata().getState();

                        Map<String, Object> response = createCartResponse(cart, state);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        logger.error("Error converting cart data: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                } else {
                    return ResponseEntity.notFound().<Map<String, Object>>build();
                }
            }).exceptionally(throwable -> {
                logger.error("Error getting cart {}: {}", cartId, throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in getCart: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @PostMapping("/{cartId}/lines")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request) {

        logger.info("Adding item to cart {} with request: {}", cartId, request);

        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            if (sku == null || qty == null || qty <= 0) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            // Get cart entity ID first
            return getCartEntityId(cartId).thenCompose(entityId -> {
                if (entityId == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                }

                // Apply ADD_ITEM transition
                return entityService.applyTransition(entityId, "ADD_ITEM")
                    .thenApply(result -> getCartResponse(cartId))
                    .exceptionally(throwable -> {
                        logger.error("Error adding item to cart: {}", throwable.getMessage(), throwable);
                        return ResponseEntity.internalServerError().build();
                    });
            });

        } catch (Exception e) {
            logger.error("Error in addItemToCart: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @PatchMapping("/{cartId}/lines")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> updateItemInCart(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request) {

        logger.info("Updating item in cart {} with request: {}", cartId, request);

        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            if (sku == null || qty == null || qty < 0) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            return getCartEntityId(cartId).thenCompose(entityId -> {
                if (entityId == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                }

                String transition = qty == 0 ? "REMOVE_ITEM" : "UPDATE_ITEM";
                return entityService.applyTransition(entityId, transition)
                    .thenApply(result -> getCartResponse(cartId))
                    .exceptionally(throwable -> {
                        logger.error("Error updating item in cart: {}", throwable.getMessage(), throwable);
                        return ResponseEntity.internalServerError().build();
                    });
            });

        } catch (Exception e) {
            logger.error("Error in updateItemInCart: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @PostMapping("/{cartId}/open-checkout")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> openCheckout(@PathVariable String cartId) {
        logger.info("Opening checkout for cart: {}", cartId);

        try {
            return getCartEntityId(cartId).thenCompose(entityId -> {
                if (entityId == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                }

                return entityService.applyTransition(entityId, "OPEN_CHECKOUT")
                    .thenApply(result -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("cartId", cartId);
                        response.put("state", "CHECKING_OUT");
                        return ResponseEntity.ok(response);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Error opening checkout: {}", throwable.getMessage(), throwable);
                        return ResponseEntity.internalServerError().build();
                    });
            });

        } catch (Exception e) {
            logger.error("Error in openCheckout: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
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

    private ResponseEntity<Map<String, Object>> getCartResponse(String cartId) {
        try {
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
                        String state = optionalPayload.get().getMetadata().getState();

                        Map<String, Object> response = createCartResponse(cart, state);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        logger.error("Error converting cart data: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                } else {
                    return ResponseEntity.notFound().<Map<String, Object>>build();
                }
            }).join();
        } catch (Exception e) {
            logger.error("Error getting cart response: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> createCartResponse(Cart cart, String state) {
        Map<String, Object> response = new HashMap<>();
        response.put("cartId", cart.getCartId());
        response.put("lines", cart.getLines());
        response.put("totalItems", cart.getTotalItems());
        response.put("grandTotal", cart.getGrandTotal());
        response.put("state", state);

        if (cart.getGuestContact() != null) {
            response.put("guestContact", cart.getGuestContact());
        }

        return response;
    }
}