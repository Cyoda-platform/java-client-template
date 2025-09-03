package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ui/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;

    public CartController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Object> createOrGetCart(@RequestBody Map<String, Object> request) {
        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            logger.info("Creating or getting cart with first item - SKU: {}, Qty: {}", sku, qty);

            if (sku == null || qty == null || qty <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_REQUEST", "message", "SKU and positive quantity are required"));
            }

            // Create new cart
            Cart cart = new Cart();
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            // Save cart with CREATE_ON_FIRST_ADD transition and line item data
            Map<String, Object> lineItemData = new HashMap<>();
            lineItemData.put("sku", sku);
            lineItemData.put("qty", qty);

            // Create payload with line item data for processor
            Map<String, Object> payload = new HashMap<>();
            payload.put("lineItemData", lineItemData);

            // For now, save the cart and then update it with the processor logic
            var savedCartResponse = entityService.save(cart);
            UUID cartEntityId = savedCartResponse.getMetadata().getId();

            // Update with line item using CREATE_ON_FIRST_ADD transition
            var updatedCartResponse = entityService.update(cartEntityId, cart, "CREATE_ON_FIRST_ADD");
            Cart updatedCart = updatedCartResponse.getData();

            logger.info("Cart created with ID: {}", updatedCart.getCartId());
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error creating cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "CART_CREATION_ERROR", "message", "Failed to create cart: " + e.getMessage()));
        }
    }

    @PostMapping("/{cartId}/lines")
    public ResponseEntity<Object> addItemToCart(@PathVariable String cartId, @RequestBody Map<String, Object> request) {
        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            logger.info("Adding item to cart {} - SKU: {}, Qty: {}", cartId, sku, qty);

            if (sku == null || qty == null || qty <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_REQUEST", "message", "SKU and positive quantity are required"));
            }

            // Find cart
            var cartResponses = entityService.findByField(
                    Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, "cartId", cartId);

            if (cartResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponses.get(0).getData();
            UUID cartEntityId = cartResponses.get(0).getMetadata().getId();

            // Update cart with ADD_ITEM transition
            var updatedCartResponse = entityService.update(cartEntityId, cart, "ADD_ITEM");
            Cart updatedCart = updatedCartResponse.getData();

            logger.info("Item added to cart: {}", cartId);
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error adding item to cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "CART_UPDATE_ERROR", "message", "Failed to add item to cart: " + e.getMessage()));
        }
    }

    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<Object> updateCartItem(@PathVariable String cartId, @RequestBody Map<String, Object> request) {
        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            logger.info("Updating cart item {} - SKU: {}, Qty: {}", cartId, sku, qty);

            if (sku == null || qty == null || qty < 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_REQUEST", "message", "SKU and non-negative quantity are required"));
            }

            // Find cart
            var cartResponses = entityService.findByField(
                    Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, "cartId", cartId);

            if (cartResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponses.get(0).getData();
            UUID cartEntityId = cartResponses.get(0).getMetadata().getId();

            // Choose transition based on quantity
            String transition = qty > 0 ? "DECREMENT_ITEM" : "REMOVE_ITEM";

            // Update cart
            var updatedCartResponse = entityService.update(cartEntityId, cart, transition);
            Cart updatedCart = updatedCartResponse.getData();

            logger.info("Cart item updated: {}", cartId);
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error updating cart item {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "CART_UPDATE_ERROR", "message", "Failed to update cart item: " + e.getMessage()));
        }
    }

    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<Object> openCheckout(@PathVariable String cartId) {
        try {
            logger.info("Opening checkout for cart: {}", cartId);

            // Find cart
            var cartResponses = entityService.findByField(
                    Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, "cartId", cartId);

            if (cartResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponses.get(0).getData();
            UUID cartEntityId = cartResponses.get(0).getMetadata().getId();

            // Update cart with OPEN_CHECKOUT transition
            var updatedCartResponse = entityService.update(cartEntityId, cart, "OPEN_CHECKOUT");
            Cart updatedCart = updatedCartResponse.getData();
            String newState = updatedCartResponse.getMetadata().getState();

            Map<String, Object> response = new HashMap<>();
            response.put("cartId", updatedCart.getCartId());
            response.put("status", newState);
            response.put("lines", updatedCart.getLines());
            response.put("totalItems", updatedCart.getTotalItems());
            response.put("grandTotal", updatedCart.getGrandTotal());

            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error opening checkout for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "CHECKOUT_ERROR", "message", "Failed to open checkout: " + e.getMessage()));
        }
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Object> getCart(@PathVariable String cartId) {
        try {
            logger.info("Getting cart details: {}", cartId);

            // Find cart
            var cartResponses = entityService.findByField(
                    Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, "cartId", cartId);

            if (cartResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponses.get(0).getData();
            return ResponseEntity.ok(cart);

        } catch (Exception e) {
            logger.error("Error getting cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "CART_ERROR", "message", "Failed to get cart: " + e.getMessage()));
        }
    }
}
