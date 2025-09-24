package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Cart Controller for OMS shopping cart management
 * Provides REST endpoints for cart operations and checkout flow
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
        logger.info("Creating new cart");

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

            EntityWithMetadata<Cart> savedCart = entityService.create(cart);
            UUID technicalId = savedCart.metadata().getId();

            logger.info("Created cart with ID: {} (technical: {})", cartId, technicalId);
            return ResponseEntity.ok(savedCart);

        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get cart by cart ID
     * GET /ui/cart/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        logger.info("Getting cart: {}", cartId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Cart.ENTITY_NAME);
            modelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cart = entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
            
            if (cart != null) {
                logger.info("Found cart: {} with {} items", cartId, cart.entity().getTotalItems());
                return ResponseEntity.ok(cart);
            } else {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
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
        
        logger.info("Adding item to cart: {} - SKU: {}, qty: {}", cartId, request.getSku(), request.getQty());

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Cart.ENTITY_NAME);
            modelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
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
                newLine.setName(request.getName());
                newLine.setPrice(request.getPrice());
                newLine.setQty(request.getQty());
                cart.getLines().add(newLine);
            }

            // Update cart status if it's NEW
            if ("NEW".equals(cart.getStatus())) {
                cart.setStatus("ACTIVE");
            }

            // Save with transition to trigger recalculation
            String transition = "NEW".equals(cartWithMetadata.entity().getStatus()) ? "create_on_first_add" : "add_item";
            EntityWithMetadata<Cart> updatedCart = entityService.updateByBusinessId(cart, "cartId", transition);

            logger.info("Added item to cart: {}", cartId);
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update item quantity in cart
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateItemInCart(
            @PathVariable String cartId, 
            @RequestBody UpdateItemRequest request) {
        
        logger.info("Updating item in cart: {} - SKU: {}, qty: {}", cartId, request.getSku(), request.getQty());

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Cart.ENTITY_NAME);
            modelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Find existing line
            Cart.CartLine existingLine = cart.getLines().stream()
                    .filter(line -> request.getSku().equals(line.getSku()))
                    .findFirst()
                    .orElse(null);

            if (existingLine == null) {
                logger.warn("Item not found in cart: {} - SKU: {}", cartId, request.getSku());
                return ResponseEntity.notFound().build();
            }

            if (request.getQty() <= 0) {
                // Remove item if quantity is 0 or negative
                cart.getLines().remove(existingLine);
            } else {
                // Update quantity
                existingLine.setQty(request.getQty());
            }

            // Save with transition to trigger recalculation
            String transition = request.getQty() <= 0 ? "remove_item" : "decrement_item";
            EntityWithMetadata<Cart> updatedCart = entityService.updateByBusinessId(cart, "cartId", transition);

            logger.info("Updated item in cart: {}", cartId);
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error updating item in cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Open checkout for cart
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        logger.info("Opening checkout for cart: {}", cartId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Cart.ENTITY_NAME);
            modelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            cart.setStatus("CHECKING_OUT");

            EntityWithMetadata<Cart> updatedCart = entityService.updateByBusinessId(cart, "cartId", "open_checkout");

            logger.info("Opened checkout for cart: {}", cartId);
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for adding items to cart
     */
    @Data
    public static class AddItemRequest {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
    }

    /**
     * Request DTO for updating items in cart
     */
    @Data
    public static class UpdateItemRequest {
        private String sku;
        private Integer qty;
    }
}
