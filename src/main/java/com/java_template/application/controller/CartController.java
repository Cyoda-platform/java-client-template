package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
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
 * ABOUTME: REST controller for Cart management endpoints including creation, line item operations, and checkout.
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
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(response.metadata().getId())
                    .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create cart: {}", e.getMessage());
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
            logger.error("Failed to get cart {}: {}", cartId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Add or increment item in cart
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItem(
            @PathVariable String cartId,
            @RequestBody CartLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            addOrUpdateLine(cart, request.getSku(), request.getName(), request.getPrice(), request.getQty(), true);
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "ADD_ITEM");
            logger.info("Item added to cart {}: {}", cartId, request.getSku());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to add item to cart {}: {}", cartId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to add item to cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update or remove item from cart
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateItem(
            @PathVariable String cartId,
            @RequestBody CartLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            if (request.getQty() == null || request.getQty() <= 0) {
                // Remove item
                cart.setLines(cart.getLines().stream()
                        .filter(line -> !line.getSku().equals(request.getSku()))
                        .toList());
            } else {
                // Update item quantity
                addOrUpdateLine(cart, request.getSku(), request.getName(), request.getPrice(), request.getQty(), false);
            }
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "DECREMENT_ITEM");
            logger.info("Item updated in cart {}: {}", cartId, request.getSku());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update item in cart {}: {}", cartId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to update item in cart: %s", e.getMessage())
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
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "OPEN_CHECKOUT");
            logger.info("Checkout opened for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to open checkout for cart {}: {}", cartId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to open checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    private void addOrUpdateLine(Cart cart, String sku, String name, Double price, Integer qty, boolean increment) {
        List<Cart.CartLine> lines = cart.getLines();
        Cart.CartLine existingLine = lines.stream()
                .filter(line -> line.getSku().equals(sku))
                .findFirst()
                .orElse(null);

        if (existingLine != null) {
            if (increment) {
                existingLine.setQty(existingLine.getQty() + qty);
            } else {
                existingLine.setQty(qty);
            }
        } else {
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(sku);
            newLine.setName(name);
            newLine.setPrice(price);
            newLine.setQty(qty);
            lines.add(newLine);
        }
    }

    @Getter
    @Setter
    public static class CartLineRequest {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
    }
}

