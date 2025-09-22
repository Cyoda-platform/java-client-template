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
 * Cart Controller - REST endpoints for cart management
 * 
 * Provides endpoints for:
 * - Create or get cart
 * - Add/update cart lines
 * - Open checkout
 * - Read cart
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
     * Create or return cart (on first add, initialize NEW→ACTIVE)
     * POST /ui/cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        try {
            Cart cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
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
     * Get cart by ID
     * GET /ui/cart/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

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
     * Add/increment item to cart
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addCartLine(
            @PathVariable String cartId,
            @RequestBody AddCartLineRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
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
                return ResponseEntity.badRequest().build();
            }

            Product product = productWithMetadata.entity();

            // Add or update cart line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Determine transition based on current status
            String transition = "NEW".equals(cart.getStatus()) ? "add_first_item" : "add_item";

            // Update cart with transition to trigger RecalculateTotals processor
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Added {} x {} to cart {}", request.getQty(), request.getSku(), cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update/set cart line quantity
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @RequestBody UpdateCartLineRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update or remove cart line
            updateOrRemoveCartLine(cart, request.getSku(), request.getQty());

            // Update cart with transition to trigger RecalculateTotals processor
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, 
                    request.getQty() == 0 ? "remove_item" : "update_item");

            logger.info("Updated cart line {} to qty {} in cart {}", 
                       request.getSku(), request.getQty(), cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating cart line", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout (set cart to CHECKING_OUT)
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update cart status and trigger transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "open_checkout");

            logger.info("Opened checkout for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Adds or updates a cart line
     */
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
    }

    /**
     * Updates or removes a cart line
     */
    private void updateOrRemoveCartLine(Cart cart, String sku, Integer qty) {
        if (cart.getLines() == null) {
            return;
        }

        if (qty == 0) {
            // Remove line
            cart.getLines().removeIf(line -> sku.equals(line.getSku()));
        } else {
            // Update line quantity
            cart.getLines().stream()
                    .filter(line -> sku.equals(line.getSku()))
                    .findFirst()
                    .ifPresent(line -> line.setQty(qty));
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class AddCartLineRequest {
        private String sku;
        private Integer qty;
    }

    @Getter
    @Setter
    public static class UpdateCartLineRequest {
        private String sku;
        private Integer qty;
    }
}
