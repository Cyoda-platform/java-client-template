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
 * CartController - REST endpoints for cart management
 * 
 * Provides endpoints for:
 * - Cart creation (on first add)
 * - Line item management (add, update, remove)
 * - Checkout initiation
 * - Cart retrieval
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
     * Get cart by cart ID
     * GET /ui/cart/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> response = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting cart by ID: {}", cartId, e);
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
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();

            // Get product details
            Product product = getProductBySku(request.getSku());
            if (product == null) {
                return ResponseEntity.badRequest().build();
            }

            // Check stock availability
            if (product.getQuantityAvailable() < request.getQty()) {
                logger.warn("Insufficient stock for product: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            // Add or update line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Determine transition based on current status
            String transition = "NEW".equals(cart.getStatus()) ? "create_on_first_add" : "add_item";

            // Update cart with recalculation
            EntityWithMetadata<Cart> response = entityService.update(cartResponse.metadata().getId(), cart, transition);
            logger.info("Added {} x {} to cart {}", request.getQty(), request.getSku(), cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update item quantity in cart (set or decrement)
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();

            // Update or remove line
            String transition;
            if (request.getQty() <= 0) {
                removeCartLine(cart, request.getSku());
                transition = "remove_item";
            } else {
                updateCartLineQuantity(cart, request.getSku(), request.getQty());
                transition = "decrement_item";
            }

            // Update cart with recalculation
            EntityWithMetadata<Cart> response = entityService.update(cartResponse.metadata().getId(), cart, transition);
            logger.info("Updated cart {} line {} to qty {}", cartId, request.getSku(), request.getQty());
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
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();

            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.warn("Cannot open checkout for empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Update cart to CHECKING_OUT
            EntityWithMetadata<Cart> response = entityService.update(cartResponse.metadata().getId(), cart, "open_checkout");
            logger.info("Opened checkout for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get product by SKU
     */
    private Product getProductBySku(String sku) {
        try {
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productResponse = entityService.findByBusinessId(
                    productModelSpec, sku, "sku", Product.class);
            return productResponse != null ? productResponse.entity() : null;
        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return null;
        }
    }

    /**
     * Add or update cart line
     */
    private void addOrUpdateCartLine(Cart cart, Product product, int qtyToAdd) {
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
            existingLine.setQty(existingLine.getQty() + qtyToAdd);
            existingLine.setLineTotal(existingLine.getPrice() * existingLine.getQty());
        } else {
            // Add new line
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(product.getSku());
            newLine.setName(product.getName());
            newLine.setPrice(product.getPrice());
            newLine.setQty(qtyToAdd);
            newLine.setLineTotal(product.getPrice() * qtyToAdd);
            cart.getLines().add(newLine);
        }
    }

    /**
     * Update cart line quantity
     */
    private void updateCartLineQuantity(Cart cart, String sku, int newQty) {
        if (cart.getLines() != null) {
            cart.getLines().stream()
                    .filter(line -> sku.equals(line.getSku()))
                    .findFirst()
                    .ifPresent(line -> {
                        line.setQty(newQty);
                        line.setLineTotal(line.getPrice() * newQty);
                    });
        }
    }

    /**
     * Remove cart line
     */
    private void removeCartLine(Cart cart, String sku) {
        if (cart.getLines() != null) {
            cart.getLines().removeIf(line -> sku.equals(line.getSku()));
        }
    }

    /**
     * Request DTO for adding items
     */
    @Getter
    @Setter
    public static class AddItemRequest {
        private String sku;
        private Integer qty;
    }

    /**
     * Request DTO for updating items
     */
    @Getter
    @Setter
    public static class UpdateItemRequest {
        private String sku;
        private Integer qty;
    }
}
