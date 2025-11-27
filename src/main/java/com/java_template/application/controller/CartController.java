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
 * ABOUTME: REST controller for Cart endpoints supporting cart creation,
 * line item management, and checkout operations.
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
            // For simplicity, create a new cart each time
            // In production, you might want to return existing cart for a session
            Cart cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setStatus("NEW");
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
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable UUID cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> response = entityService.getById(cartId, modelSpec, Cart.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to get cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to get cart: %s", e.getMessage())
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
            @PathVariable UUID cartId,
            @Valid @RequestBody AddLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.getById(cartId, modelSpec, Cart.class);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Find or create line
            Cart.CartLine existingLine = cart.getLines().stream()
                    .filter(line -> line.getSku().equals(request.getSku()))
                    .findFirst()
                    .orElse(null);

            if (existingLine != null) {
                existingLine.setQty(existingLine.getQty() + request.getQty());
            } else {
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(request.getSku());
                newLine.setName(request.getName());
                newLine.setPrice(request.getPrice());
                newLine.setQty(request.getQty());
                cart.getLines().add(newLine);
            }

            // Trigger recalculation via transition
            String transition = cart.getStatus().equals("NEW") ? "create_on_first_add" : "add_item";
            EntityWithMetadata<Cart> response = entityService.update(cartId, cart, transition);
            logger.info("Item added to cart: {}", cartId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to add item to cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to add item: %s", e.getMessage())
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
            @PathVariable UUID cartId,
            @Valid @RequestBody UpdateLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.getById(cartId, modelSpec, Cart.class);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            if (request.getQty() == 0) {
                // Remove item
                cart.getLines().removeIf(line -> line.getSku().equals(request.getSku()));
                EntityWithMetadata<Cart> response = entityService.update(cartId, cart, "remove_item");
                logger.info("Item removed from cart: {}", cartId);
                return ResponseEntity.ok(response);
            } else {
                // Update quantity
                Cart.CartLine line = cart.getLines().stream()
                        .filter(l -> l.getSku().equals(request.getSku()))
                        .findFirst()
                        .orElse(null);

                if (line != null) {
                    line.setQty(request.getQty());
                    EntityWithMetadata<Cart> response = entityService.update(cartId, cart, "decrement_item");
                    logger.info("Item quantity updated in cart: {}", cartId);
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.notFound().build();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to update item in cart: {}", cartId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to update item: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Open checkout
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable UUID cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.getById(cartId, modelSpec, Cart.class);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            EntityWithMetadata<Cart> response = entityService.update(cartId, cart, "open_checkout");
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

