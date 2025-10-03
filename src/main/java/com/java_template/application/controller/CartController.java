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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: This file contains the CartController that exposes REST APIs for shopping cart
 * management including line item operations and checkout flow for anonymous users.
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
     * Create or return existing cart (for first add)
     * POST /ui/cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        try {
            // Generate unique cart ID
            String cartId = "CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Cart cart = new Cart();
            cart.setCartId(cartId);
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", cartId);
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
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> response = entityService.findByBusinessIdOrNull(
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
    public ResponseEntity<?> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            // Get product to validate and get current price/name
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productResponse = entityService.findByBusinessIdOrNull(
                    productModelSpec, request.getSku(), "sku", Product.class);

            if (productResponse == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Product not found with SKU: " + request.getSku());
                return ResponseEntity.of(problemDetail).build();
            }

            Product product = productResponse.entity();
            
            // Check stock availability
            if (product.getQuantityAvailable() < request.getQty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    String.format("Insufficient stock. Available: %d, Requested: %d", 
                        product.getQuantityAvailable(), request.getQty()));
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartResponse.entity();
            
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
                // Increment existing line
                existingLine.setQty(existingLine.getQty() + request.getQty());
            } else {
                // Add new line
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(request.getSku());
                newLine.setName(product.getName());
                newLine.setPrice(product.getPrice());
                newLine.setQty(request.getQty());
                cart.getLines().add(newLine);
            }

            // Update cart status to ACTIVE if it was NEW
            if ("NEW".equals(cart.getStatus())) {
                cart.setStatus("ACTIVE");
            }

            // Update cart with transition to trigger RecalculateTotals processor
            String transition = "NEW".equals(cartResponse.entity().getStatus()) ? 
                    "create_on_first_add" : "add_item";
            
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartResponse.metadata().getId(), cart, transition);
            
            logger.info("Item {} added to cart {}", request.getSku(), cartId);
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update item quantity in cart (set/decrement, remove if qty=0)
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<?> updateCartLine(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();
            
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Find the line to update
            Cart.CartLine lineToUpdate = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (lineToUpdate == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Item not found in cart: " + request.getSku());
                return ResponseEntity.of(problemDetail).build();
            }

            if (request.getQty() <= 0) {
                // Remove item from cart
                cart.getLines().remove(lineToUpdate);
            } else {
                // Update quantity
                lineToUpdate.setQty(request.getQty());
            }

            // Update cart with transition to trigger RecalculateTotals processor
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartResponse.metadata().getId(), cart, "remove_item");
            
            logger.info("Cart line {} updated in cart {}", request.getSku(), cartId);
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Error updating cart line", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout (set cart to CHECKING_OUT status)
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<?> openCheckout(@PathVariable String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();
            
            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty() || cart.getTotalItems() == 0) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cannot checkout empty cart");
                return ResponseEntity.of(problemDetail).build();
            }

            // Update cart status to CHECKING_OUT
            cart.setStatus("CHECKING_OUT");
            
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartResponse.metadata().getId(), cart, "open_checkout");
            
            logger.info("Checkout opened for cart {}", cartId);
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Error opening checkout for cart", e);
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
