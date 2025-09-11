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
import java.util.ArrayList;
import java.util.UUID;

/**
 * CartController - Manages shopping cart operations for anonymous checkout.
 * 
 * Endpoints:
 * - POST /ui/cart - Create new cart
 * - POST /ui/cart/{cartId}/lines - Add or increment item in cart
 * - PATCH /ui/cart/{cartId}/lines - Update or remove item in cart
 * - POST /ui/cart/{cartId}/open-checkout - Set cart to checkout mode
 * - GET /ui/cart/{cartId} - Get cart details
 */
@RestController
@RequestMapping("/ui/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;

    public CartController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create new cart
     * POST /ui/cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        try {
            Cart cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            
            LocalDateTime now = LocalDateTime.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.metadata().getId());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add or increment item in cart
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        
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
            String currentState = cartWithMetadata.metadata().getState();

            // Add or update line item
            boolean itemFound = false;
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getSku().equals(request.getSku())) {
                    line.setQty(line.getQty() + request.getQty());
                    itemFound = true;
                    break;
                }
            }

            if (!itemFound) {
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(request.getSku());
                newLine.setQty(request.getQty());
                // Price and name will be set by the processor
                cart.getLines().add(newLine);
            }

            // Determine transition based on current state
            String transition = null;
            if ("NEW".equals(currentState)) {
                transition = "ADD_FIRST_ITEM";
            } else if ("ACTIVE".equals(currentState)) {
                transition = "MODIFY_ITEMS";
            }

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Item added to cart {}: SKU={}, qty={}", cartId, request.getSku(), request.getQty());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update or remove item in cart (remove if qty=0)
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateItemInCart(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        
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

            // Update or remove line item
            cart.getLines().removeIf(line -> {
                if (line.getSku().equals(request.getSku())) {
                    if (request.getQty() == 0) {
                        return true; // Remove item
                    } else {
                        line.setQty(request.getQty());
                        return false; // Keep item with updated quantity
                    }
                }
                return false;
            });

            String transition = "MODIFY_ITEMS";
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Item updated in cart {}: SKU={}, qty={}", cartId, request.getSku(), request.getQty());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating item in cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Set cart to checkout mode
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
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
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "OPEN_CHECKOUT");
            
            logger.info("Cart {} opened for checkout", cartId);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cart details
     * GET /ui/cart/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(cartWithMetadata);

        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Request DTO for adding items to cart
     */
    @Data
    public static class AddItemRequest {
        private String sku;
        private Integer qty;
    }

    /**
     * Request DTO for updating items in cart
     */
    @Data
    public static class UpdateItemRequest {
        private String sku;
        private Integer qty;
    }
}
