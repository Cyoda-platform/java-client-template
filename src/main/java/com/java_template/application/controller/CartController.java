package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart Controller for OMS system
 * Handles shopping cart operations including creation, line management, and checkout
 */
@RestController
@RequestMapping("/ui/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CartController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create or return existing cart
     * POST /ui/cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        try {
            Cart cart = new Cart();
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", cart.getCartId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cart by cart ID
     * GET /ui/cart/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cart = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cart == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(cart);
        } catch (Exception e) {
            logger.error("Error getting cart by ID: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add item to cart
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Get product details
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, request.getSku(), "sku", Product.class);

            if (productWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            Product product = productWithMetadata.entity();

            // Check if item already exists in cart
            Cart.CartLine existingLine = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (existingLine != null) {
                // Update existing line
                existingLine.setQty(existingLine.getQty() + request.getQty());
            } else {
                // Add new line
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(request.getSku());
                newLine.setName(product.getName());
                newLine.setPrice(product.getPrice());
                newLine.setQty(request.getQty());
                cart.getLines().add(newLine);
            }

            // Update cart status to ACTIVE if it was NEW
            if ("NEW".equals(cart.getStatus())) {
                cart.setStatus("ACTIVE");
            }

            // Update cart with recalculation
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "add_item");
            
            logger.info("Added {} x {} to cart {}", request.getQty(), request.getSku(), cartId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update item quantity in cart
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartItem(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Find and update line
            Cart.CartLine lineToUpdate = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (lineToUpdate == null) {
                return ResponseEntity.notFound().build();
            }

            if (request.getQty() <= 0) {
                // Remove item if quantity is 0 or negative
                cart.getLines().remove(lineToUpdate);
            } else {
                // Update quantity
                lineToUpdate.setQty(request.getQty());
            }

            // Update cart with recalculation
            String transition = request.getQty() <= 0 ? "remove_item" : "update_item";
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Updated cart {} item {}: qty = {}", cartId, request.getSku(), request.getQty());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating cart item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout (set cart to CHECKING_OUT status)
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "open_checkout");
            
            logger.info("Opened checkout for cart {}", cartId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class AddItemRequest {
        private String sku;
        private Integer qty;
    }

    @Getter
    @Setter
    public static class UpdateItemRequest {
        private String sku;
        private Integer qty;
    }
}
