package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart Controller - Manages shopping cart operations
 * 
 * Endpoints:
 * - POST /ui/cart - Create new cart
 * - POST /ui/cart/{cartId}/lines - Add or increment item in cart
 * - PATCH /ui/cart/{cartId}/lines - Update item quantity in cart
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
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.getId());
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
            // Get cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Get product details
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productResponse = entityService.findByBusinessId(
                productModelSpec, request.getSku(), "sku", Product.class);
            
            if (productResponse == null) {
                logger.error("Product not found: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }
            
            Cart cart = cartResponse.entity();
            Product product = productResponse.entity();

            // Add or update item in cart
            addOrUpdateCartLine(cart, product, request.getQty());

            // Determine transition based on current state
            String transition = null;
            String currentState = cartResponse.getState();
            if ("NEW".equals(currentState)) {
                transition = "CREATE_ON_FIRST_ADD";
            } else if ("ACTIVE".equals(currentState)) {
                transition = "ADD_ITEM";
            }

            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                cartResponse.getId(), cart, transition);
            logger.info("Item added to cart: {} - {}", request.getSku(), request.getQty());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update item quantity in cart (remove if qty=0)
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartItem(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            // Get cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }
            
            Cart cart = cartResponse.entity();

            // Update or remove item
            if (request.getQty() <= 0) {
                removeCartLine(cart, request.getSku());
            } else {
                // Get product details for price
                ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
                EntityWithMetadata<Product> productResponse = entityService.findByBusinessId(
                    productModelSpec, request.getSku(), "sku", Product.class);

                if (productResponse == null) {
                    logger.error("Product not found: {}", request.getSku());
                    return ResponseEntity.badRequest().build();
                }

                updateCartLine(cart, productResponse.entity(), request.getQty());
            }

            cart.setUpdatedAt(LocalDateTime.now());

            String transition = request.getQty() <= 0 ? "REMOVE_ITEM" : "UPDATE_ITEM";
            EntityWithMetadata<Cart> response = entityService.update(
                cartResponse.getId(), cart, transition);
            logger.info("Cart item updated: {} - {}", request.getSku(), request.getQty());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating cart item", e);
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
            // Get cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }
            
            Cart cart = cartResponse.entity();

            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.error("Cannot open checkout for empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                cartResponse.getId(), cart, "OPEN_CHECKOUT");
            logger.info("Cart opened for checkout: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error opening cart for checkout", e);
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
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> response = entityService.findByBusinessId(
                cartModelSpec, cartId, "cartId", Cart.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private void addOrUpdateCartLine(Cart cart, Product product, Integer qty) {
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }
        
        // Find existing line
        Cart.CartLine existingLine = cart.getLines().stream()
            .filter(line -> product.getSku().equals(line.getSku()))
            .findFirst()
            .orElse(null);
        
        if (existingLine != null) {
            // Update existing line
            existingLine.setQty(existingLine.getQty() + qty);
        } else {
            // Add new line
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(product.getSku());
            newLine.setName(product.getName());
            newLine.setPrice(product.getPrice());
            newLine.setQty(qty);
            cart.getLines().add(newLine);
        }
        
        recalculateTotals(cart);
    }

    private void updateCartLine(Cart cart, Product product, Integer qty) {
        if (cart.getLines() != null) {
            cart.getLines().stream()
                .filter(line -> product.getSku().equals(line.getSku()))
                .findFirst()
                .ifPresent(line -> line.setQty(qty));
        }
        recalculateTotals(cart);
    }

    private void removeCartLine(Cart cart, String sku) {
        if (cart.getLines() != null) {
            cart.getLines().removeIf(line -> sku.equals(line.getSku()));
        }
        recalculateTotals(cart);
    }

    private void recalculateTotals(Cart cart) {
        if (cart.getLines() == null) {
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            return;
        }
        
        int totalItems = cart.getLines().stream()
            .mapToInt(Cart.CartLine::getQty)
            .sum();
        
        double grandTotal = cart.getLines().stream()
            .mapToDouble(line -> line.getPrice() * line.getQty())
            .sum();
        
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
    }

    // Request DTOs

    public static class AddItemRequest {
        private String sku;
        private Integer qty;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
    }

    public static class UpdateItemRequest {
        private String sku;
        private Integer qty;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
    }
}
