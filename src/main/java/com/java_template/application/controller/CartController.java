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
 * Cart Controller for shopping cart management
 * Provides endpoints for cart creation, line management, and checkout initiation
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
            // Create new cart
            Cart cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            
            LocalDateTime now = LocalDateTime.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.entity().getCartId());
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
     * Add item to cart
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            // Find cart
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

            // Initialize lines if null
            if (cart.getLines() == null) {
                cart.setLines(new ArrayList<>());
            }

            // Find existing line or create new one
            Cart.CartLine existingLine = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (existingLine != null) {
                // Update existing line
                existingLine.setQty(existingLine.getQty() + request.getQty());
                existingLine.setLineTotal(existingLine.getPrice() * existingLine.getQty());
            } else {
                // Add new line
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(request.getSku());
                newLine.setName(product.getName());
                newLine.setPrice(product.getPrice());
                newLine.setQty(request.getQty());
                newLine.setLineTotal(product.getPrice() * request.getQty());
                cart.getLines().add(newLine);
            }

            // Determine transition based on cart status
            String transition = "NEW".equals(cart.getStatus()) ? "create_on_first_add" : "add_item";

            // Update cart
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Item {} added to cart {}", request.getSku(), cartId);
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
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            // Find cart
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            if (cart.getLines() == null) {
                return ResponseEntity.badRequest().build();
            }

            // Find and update line
            Cart.CartLine lineToUpdate = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (lineToUpdate == null) {
                return ResponseEntity.badRequest().build();
            }

            String transition;
            if (request.getQty() <= 0) {
                // Remove item
                cart.getLines().remove(lineToUpdate);
                transition = "remove_item";
            } else {
                // Update quantity
                lineToUpdate.setQty(request.getQty());
                lineToUpdate.setLineTotal(lineToUpdate.getPrice() * request.getQty());
                transition = "decrement_item";
            }

            // Update cart
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Cart line updated for cart {}, SKU {}", cartId, request.getSku());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating cart line", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout (transition to CHECKING_OUT)
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            // Find cart
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update cart to CHECKING_OUT
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "open_checkout");

            logger.info("Checkout opened for cart {}", cartId);
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
