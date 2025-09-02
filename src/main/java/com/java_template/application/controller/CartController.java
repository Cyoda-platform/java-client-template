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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ui/cart")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;

    public CartController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCart() {
        try {
            logger.info("Creating new cart");

            // Create new cart
            Cart cart = new Cart();
            cart.setCartId("cart_" + UUID.randomUUID().toString().substring(0, 8));
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(Instant.now());
            cart.setUpdatedAt(Instant.now());

            // Save cart - this will trigger create_on_first_add -> new -> activate_cart transitions
            EntityResponse<Cart> savedCart = entityService.save(cart);

            Map<String, Object> response = convertToCartResponse(savedCart.getData(), savedCart.getMetadata().getState());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/lines")
    public ResponseEntity<Map<String, Object>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request) {
        
        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");
            
            logger.info("Adding item to cart: {} - SKU: {}, Qty: {}", cartId, sku, qty);

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

            // TODO: Add item data to cart entity for processor to use
            // For now, we'll simulate the add item operation
            
            // Update cart with add_item transition
            EntityResponse<Cart> updatedCart = entityService.update(cartEntityId, cart, "add_item");

            Map<String, Object> response = convertToCartResponse(updatedCart.getData(), updatedCart.getMetadata().getState());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<Map<String, Object>> updateItemInCart(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request) {
        
        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");
            
            logger.info("Updating item in cart: {} - SKU: {}, Qty: {}", cartId, sku, qty);

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

            // Determine transition based on quantity
            String transition = (qty != null && qty > 0) ? "update_item" : "remove_item";
            
            // Update cart with appropriate transition
            EntityResponse<Cart> updatedCart = entityService.update(cartEntityId, cart, transition);

            Map<String, Object> response = convertToCartResponse(updatedCart.getData(), updatedCart.getMetadata().getState());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating item in cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<Map<String, Object>> openCheckout(@PathVariable String cartId) {
        try {
            logger.info("Opening checkout for cart: {}", cartId);

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

            // Update cart with open_checkout transition
            EntityResponse<Cart> updatedCart = entityService.update(cartEntityId, cart, "open_checkout");

            Map<String, Object> response = convertToCartResponse(updatedCart.getData(), updatedCart.getMetadata().getState());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Map<String, Object>> getCart(@PathVariable String cartId) {
        try {
            logger.info("Getting cart: {}", cartId);

            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.notFound().build();
            }

            // Get cart state
            String state = getCartState(cartId);
            
            Map<String, Object> response = convertToCartResponse(cart, state);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
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

    private String getCartState(String cartId) {
        // TODO: Implement proper state lookup
        return "active";
    }

    private Map<String, Object> convertToCartResponse(Cart cart, String state) {
        Map<String, Object> response = new HashMap<>();
        response.put("cartId", cart.getCartId());
        response.put("state", state);
        response.put("lines", cart.getLines());
        response.put("totalItems", cart.getTotalItems());
        response.put("grandTotal", cart.getGrandTotal());
        response.put("guestContact", cart.getGuestContact());
        return response;
    }
}
