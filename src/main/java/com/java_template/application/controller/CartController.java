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
 * Cart Controller for OMS
 * Provides endpoints for cart management including create, add/update lines, checkout
 * Maps to /ui/cart/** endpoints
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
     * Create or return existing cart
     * POST /ui/cart
     * Creates new cart on first add, initializes NEW→ACTIVE
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
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cart = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cart == null) {
                logger.warn("Cart not found for ID: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(cart);
        } catch (Exception e) {
            logger.error("Error getting cart by ID: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add item to cart or increment quantity
     * POST /ui/cart/{cartId}/lines
     * Body: { sku, qty }
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        try {
            // Get cart
            EntityWithMetadata<Cart> cartWithMetadata = getCartByBusinessId(cartId);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Get product details
            Product product = getProductBySku(request.getSku());
            if (product == null) {
                logger.warn("Product not found for SKU: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            // Add or update line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Update cart with transition to trigger RecalculateTotals processor
            String transition = cart.getLines().size() == 1 ? "create_on_first_add" : "add_item";
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
     * Update item quantity in cart
     * PATCH /ui/cart/{cartId}/lines
     * Body: { sku, qty } - sets quantity (removes if qty=0)
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            // Get cart
            EntityWithMetadata<Cart> cartWithMetadata = getCartByBusinessId(cartId);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update or remove line
            String transition;
            if (request.getQty() == 0) {
                removeCartLine(cart, request.getSku());
                transition = "remove_item";
            } else {
                // Get product details for price
                Product product = getProductBySku(request.getSku());
                if (product == null) {
                    return ResponseEntity.badRequest().build();
                }
                updateCartLine(cart, product, request.getQty());
                transition = "decrement_item";
            }

            // Update cart with transition to trigger RecalculateTotals processor
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Updated cart {} line {} to qty {}", cartId, request.getSku(), request.getQty());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating cart line", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout process
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            EntityWithMetadata<Cart> cartWithMetadata = getCartByBusinessId(cartId);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update cart to CHECKING_OUT state
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "open_checkout");

            logger.info("Opened checkout for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private EntityWithMetadata<Cart> getCartByBusinessId(String cartId) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);
        return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
    }

    private Product getProductBySku(String sku) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);
        EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                modelSpec, sku, "sku", Product.class);
        return productWithMetadata != null ? productWithMetadata.entity() : null;
    }

    private void addOrUpdateCartLine(Cart cart, Product product, Integer qty) {
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

    private void updateCartLine(Cart cart, Product product, Integer qty) {
        Cart.CartLine line = cart.getLines().stream()
                .filter(l -> product.getSku().equals(l.getSku()))
                .findFirst()
                .orElse(null);

        if (line != null) {
            line.setQty(qty);
        }
    }

    private void removeCartLine(Cart cart, String sku) {
        cart.getLines().removeIf(line -> sku.equals(line.getSku()));
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
