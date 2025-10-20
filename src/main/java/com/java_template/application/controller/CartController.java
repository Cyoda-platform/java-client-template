package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Cart controller providing REST endpoints for shopping cart management
 * including line item operations and checkout flow initiation.
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
            // Create new cart
            Cart cart = new Cart();
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8));
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.entity().getCartId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{cartId}")
                .buildAndExpand(response.entity().getCartId())
                .toUri();

            return ResponseEntity.created(location).body(response);

        } catch (Exception e) {
            logger.error("Failed to create cart", e);
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
            logger.error("Failed to get cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to get cart '%s': %s", cartId, e.getMessage())
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
            @Valid @RequestBody AddItemRequest request) {
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
            ModelSpec productModelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, request.getSku(), "sku", Product.class);

            if (productWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Product not found with SKU: %s", request.getSku())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Product product = productWithMetadata.entity();

            // Check stock availability
            if (product.getQuantityAvailable() < request.getQty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Insufficient stock for SKU %s. Available: %d, Requested: %d",
                            request.getSku(), product.getQuantityAvailable(), request.getQty())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Add or update cart line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Update cart status to ACTIVE if it was NEW
            if ("NEW".equals(cart.getStatus())) {
                cart.setStatus("ACTIVE");
            }

            // Update cart with recalculation
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "add_item");

            logger.info("Item added to cart {}: SKU {} x {}", cartId, request.getSku(), request.getQty());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to add item to cart: {}", cartId, e);
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
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @Valid @RequestBody UpdateItemRequest request) {
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
            if (request.getQty() <= 0) {
                removeCartLine(cart, request.getSku());
            } else {
                updateCartLineQuantity(cart, request.getSku(), request.getQty());
            }

            // Update cart with recalculation
            String transition = request.getQty() <= 0 ? "remove_item" : "decrement_item";
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Cart line updated for cart {}: SKU {} -> qty {}", cartId, request.getSku(), request.getQty());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to update cart line: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update cart line: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Open checkout for cart
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

            if (!"ACTIVE".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart must be ACTIVE to open checkout. Current status: %s", cart.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Update cart status to CHECKING_OUT
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "open_checkout");

            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to open checkout for cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to open checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Helper method to add or update cart line
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
     * Helper method to update cart line quantity
     */
    private void updateCartLineQuantity(Cart cart, String sku, Integer qty) {
        if (cart.getLines() != null) {
            cart.getLines().stream()
                    .filter(line -> sku.equals(line.getSku()))
                    .findFirst()
                    .ifPresent(line -> line.setQty(qty));
        }
    }

    /**
     * Helper method to remove cart line
     */
    private void removeCartLine(Cart cart, String sku) {
        if (cart.getLines() != null) {
            cart.getLines().removeIf(line -> sku.equals(line.getSku()));
        }
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
