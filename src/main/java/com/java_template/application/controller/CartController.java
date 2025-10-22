package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Cart controller providing shopping cart management with line item
 * operations, totals calculation, and checkout preparation.
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
            logger.error("Failed to create cart: {}", e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            logger.error("Failed to get cart {}: {}", cartId, e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Get product details
            Product product = getProductBySku(request.getSku());
            if (product == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Product not found with SKU: %s", request.getSku())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Add or update cart line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Determine transition based on current status
            String transition = "NEW".equals(cart.getStatus()) ? "create_on_first_add" : "add_item";
            
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Added {} x {} to cart {}", request.getQty(), request.getSku(), cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to add item to cart {}: {}", cartId, e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to add item to cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            String transition;
            if (request.getQty() <= 0) {
                removeCartLine(cart, request.getSku());
                transition = "remove_item";
            } else {
                updateCartLineQuantity(cart, request.getSku(), request.getQty());
                transition = "decrement_item";
            }

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Updated cart {} item {} to qty {}", cartId, request.getSku(), request.getQty());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update cart item {}: {}", cartId, e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update cart item: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Open checkout
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
            cart.setStatus("CHECKING_OUT");

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "open_checkout");
            
            logger.info("Opened checkout for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to open checkout for cart {}: {}", cartId, e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to open checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get product by SKU
     */
    private Product getProductBySku(String sku) {
        try {
            ModelSpec productModelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);
            
            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, sku, "sku", Product.class);
            
            return productWithMetadata != null ? productWithMetadata.entity() : null;
        } catch (Exception e) {
            logger.error("Failed to get product by SKU {}: {}", sku, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Add or update cart line
     */
    private void addOrUpdateCartLine(Cart cart, Product product, Integer qty) {
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
     * Update cart line quantity
     */
    private void updateCartLineQuantity(Cart cart, String sku, Integer qty) {
        cart.getLines().stream()
                .filter(line -> sku.equals(line.getSku()))
                .findFirst()
                .ifPresent(line -> line.setQty(qty));
    }

    /**
     * Remove cart line
     */
    private void removeCartLine(Cart cart, String sku) {
        cart.getLines().removeIf(line -> sku.equals(line.getSku()));
    }

    @Data
    public static class AddItemRequest {
        private String sku;
        private Integer qty;
    }

    @Data
    public static class UpdateItemRequest {
        private String sku;
        private Integer qty;
    }
}
