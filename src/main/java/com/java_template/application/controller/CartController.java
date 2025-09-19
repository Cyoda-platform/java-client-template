package com.java_template.application.controller;

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
 * Cart Controller - Shopping cart management for OMS
 * 
 * Provides REST endpoints for cart creation, item management, and checkout preparation.
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
     * Create new cart or return existing cart
     * POST /ui/cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        try {
            logger.info("Creating new cart");

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
     * Get cart by cartId
     * GET /ui/cart/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        try {
            logger.info("Getting cart: {}", cartId);

            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cart = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(cart);

        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add item to cart or increment quantity
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        
        try {
            logger.info("Adding item to cart: {} - SKU: {}, Qty: {}", cartId, request.getSku(), request.getQty());

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Get product details
            ModelSpec productModelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, request.getSku(), "sku", Product.class);

            if (productWithMetadata == null) {
                logger.warn("Product not found: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            Product product = productWithMetadata.entity();

            // Add or update cart line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Determine transition based on cart status
            String transition = "NEW".equals(cart.getStatus()) ? "CREATE_ON_FIRST_ADD" : "ADD_ITEM";
            
            // Update cart with transition to trigger RecalculateTotals processor
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Item added to cart: {} - Total items: {}", cartId, cart.getTotalItems());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update item quantity in cart (set/decrement)
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartItem(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        
        try {
            logger.info("Updating cart item: {} - SKU: {}, Qty: {}", cartId, request.getSku(), request.getQty());

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update or remove cart line
            String transition = updateOrRemoveCartLine(cart, request.getSku(), request.getQty());

            // Update cart with appropriate transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Cart item updated: {} - Total items: {}", cartId, cart.getTotalItems());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating cart item: {}", cartId, e);
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
            logger.info("Opening checkout for cart: {}", cartId);

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            if (!"ACTIVE".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in ACTIVE status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Update cart status to CHECKING_OUT
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "OPEN_CHECKOUT");

            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add or update cart line with product
     */
    private void addOrUpdateCartLine(Cart cart, Product product, Integer qty) {
        // Find existing line
        Cart.CartLine existingLine = cart.getLines().stream()
                .filter(line -> product.getSku().equals(line.getSku()))
                .findFirst()
                .orElse(null);

        if (existingLine != null) {
            // Update existing line
            existingLine.setQty(existingLine.getQty() + qty);
            existingLine.setLineTotal(existingLine.getPrice() * existingLine.getQty());
        } else {
            // Add new line
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(product.getSku());
            newLine.setName(product.getName());
            newLine.setPrice(product.getPrice());
            newLine.setQty(qty);
            newLine.setLineTotal(product.getPrice() * qty);
            cart.getLines().add(newLine);
        }
    }

    /**
     * Update or remove cart line
     * Returns the appropriate transition name
     */
    private String updateOrRemoveCartLine(Cart cart, String sku, Integer newQty) {
        Cart.CartLine existingLine = cart.getLines().stream()
                .filter(line -> sku.equals(line.getSku()))
                .findFirst()
                .orElse(null);

        if (existingLine == null) {
            return "DECREMENT_ITEM"; // No change needed
        }

        if (newQty <= 0) {
            // Remove line
            cart.getLines().remove(existingLine);
            return "REMOVE_ITEM";
        } else {
            // Update quantity
            existingLine.setQty(newQty);
            existingLine.setLineTotal(existingLine.getPrice() * newQty);
            return "DECREMENT_ITEM";
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
