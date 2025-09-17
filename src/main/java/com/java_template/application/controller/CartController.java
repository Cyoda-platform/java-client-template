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
import java.util.UUID;

/**
 * CartController - REST endpoints for cart management
 * 
 * Provides UI-facing endpoints for cart operations including
 * creation, item management, and checkout preparation.
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

            // Get product to validate and get current price
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productResponse = entityService.findByBusinessId(
                    productModelSpec, request.getSku(), "sku", Product.class);

            if (productResponse == null) {
                logger.error("Product not found: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            Product product = productResponse.entity();

            // Check stock availability
            if (product.getQuantityAvailable() < request.getQty()) {
                logger.error("Insufficient stock for product: {}. Available: {}, Requested: {}", 
                           request.getSku(), product.getQuantityAvailable(), request.getQty());
                return ResponseEntity.badRequest().build();
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

            // Determine transition based on current state
            String transition = null;
            String currentState = cartResponse.metadata().getState();
            if ("new".equals(currentState)) {
                transition = "ADD_FIRST_ITEM";
            } else if ("active".equals(currentState)) {
                transition = "MODIFY_ITEMS";
            }

            // Update cart
            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, transition);
            
            logger.info("Item added to cart {}: {} x {}", cartId, request.getSku(), request.getQty());
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
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartItem(
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

            // Find existing line
            Cart.CartLine existingLine = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (existingLine == null) {
                logger.error("Line item not found in cart: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            // Update quantity (remove if qty = 0)
            if (request.getQty() <= 0) {
                cart.getLines().remove(existingLine);
                logger.info("Item removed from cart {}: {}", cartId, request.getSku());
            } else {
                existingLine.setQty(request.getQty());
                logger.info("Item quantity updated in cart {}: {} x {}", cartId, request.getSku(), request.getQty());
            }

            // Update cart with MODIFY_ITEMS transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, "MODIFY_ITEMS");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating cart item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout (transition to CHECKING_OUT state)
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResponse.entity();

            // Update cart with OPEN_CHECKOUT transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartResponse.metadata().getId(), cart, "OPEN_CHECKOUT");
            
            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request DTOs
     */
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
