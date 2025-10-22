package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
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
 * ABOUTME: Cart REST controller exposing shopping cart endpoints for creating,
 * updating, and managing cart items and checkout flow.
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
    public ResponseEntity<EntityWithMetadata<Cart>> createOrGetCart() {
        try {
            // For simplicity, create a new cart with a generated ID
            Cart cart = new Cart();
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", cart.getCartId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{cartId}")
                .buildAndExpand(cart.getCartId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
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
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> response = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve cart with ID '%s': %s", cartId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Add item to cart (or increment if exists)
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @Valid @RequestBody AddLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();
            if (cart.getLines() == null) {
                cart.setLines(new ArrayList<>());
            }

            // Check if item already exists
            boolean found = false;
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getSku().equals(request.getSku())) {
                    line.setQty(line.getQty() + request.getQty());
                    found = true;
                    break;
                }
            }

            // Add new line if not found
            if (!found) {
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(request.getSku());
                newLine.setName(request.getName());
                newLine.setPrice(request.getPrice());
                newLine.setQty(request.getQty());
                cart.getLines().add(newLine);
            }

            cart.setUpdatedAt(LocalDateTime.now());

            // Update with add_item transition to trigger RecalculateTotals
            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, "add_item");
            logger.info("Item added to cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to add item to cart '%s': %s", cartId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update or remove item from cart
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @Valid @RequestBody UpdateLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();
            if (cart.getLines() == null) {
                cart.setLines(new ArrayList<>());
            }

            // Find and update or remove line
            cart.getLines().removeIf(line -> {
                if (line.getSku().equals(request.getSku())) {
                    if (request.getQty() == 0) {
                        return true; // Remove line
                    } else {
                        line.setQty(request.getQty());
                        return false;
                    }
                }
                return false;
            });

            cart.setUpdatedAt(LocalDateTime.now());

            // Update with decrement_item transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, "decrement_item");
            logger.info("Cart line updated: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update cart line '%s': %s", cartId, e.getMessage())
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
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, "open_checkout");
            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to open checkout for cart '%s': %s", cartId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Request DTOs
     */
    @Getter
    @Setter
    public static class AddLineRequest {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
    }

    @Getter
    @Setter
    public static class UpdateLineRequest {
        private String sku;
        private Integer qty;
    }
}

