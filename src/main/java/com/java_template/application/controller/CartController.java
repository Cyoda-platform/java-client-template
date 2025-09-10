package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * CartController - REST endpoints for shopping cart operations
 * 
 * Provides UI-facing APIs for cart creation, item management, and checkout preparation.
 * Base path: /ui/cart
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
     * POST /ui/cart - Create or get cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        try {
            Cart cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(BigDecimal.ZERO);
            cart.setGuestContact(null);
            
            LocalDateTime now = LocalDateTime.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.metadata().getId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /ui/cart/{cartId}/lines - Add/increment item
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Find product to get name and price
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, request.getSku(), "sku", Product.class);
            
            if (productWithMetadata == null) {
                logger.error("Product not found: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            Product product = productWithMetadata.entity();

            // Check if item already exists in cart
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

            // Update cart with ADD_ITEM transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "ADD_ITEM");
            
            logger.info("Item {} added to cart {}", request.getSku(), cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PATCH /ui/cart/{cartId}/lines - Update/remove item
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateItemInCart(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Find existing line
            Cart.CartLine existingLine = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (existingLine == null) {
                logger.error("Item not found in cart: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            String transition;
            if (request.getQty() == 0) {
                // Remove item
                cart.getLines().remove(existingLine);
                transition = "REMOVE_ITEM";
            } else {
                // Update quantity
                existingLine.setQty(request.getQty());
                transition = "UPDATE_ITEM";
            }

            // Update cart with appropriate transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Item {} updated in cart {}", request.getSku(), cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating item in cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /ui/cart/{cartId}/open-checkout - Open checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update cart with OPEN_CHECKOUT transition
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "OPEN_CHECKOUT");
            
            logger.info("Checkout opened for cart {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error opening checkout for cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /ui/cart/{cartId} - Get cart
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cart = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            
            if (cart == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(cart);
        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

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
