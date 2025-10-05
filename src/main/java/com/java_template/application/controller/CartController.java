package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Cart controller providing REST endpoints for shopping cart
 * management including add/remove items and checkout initiation.
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
            // Create new cart
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
     * Get cart by technical UUID
     * GET /ui/cart/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCartById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> response = entityService.getById(id, modelSpec, Cart.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting cart by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get cart by business ID
     * GET /ui/cart/business/{cartId}
     */
    @GetMapping("/business/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCartByBusinessId(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> response = entityService.findByBusinessIdOrNull(
                    modelSpec, cartId, "cartId", Cart.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting cart by business ID: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add item to cart (or increment if exists)
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Get product details
            Product product = getProductBySku(request.getSku());
            if (product == null) {
                return ResponseEntity.badRequest().build();
            }

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

            // Update cart with transition to recalculate totals
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "add_item");

            logger.info("Added {} x {} to cart {}", request.getQty(), request.getSku(), cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update item quantity in cart (or remove if qty=0)
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartItem(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Find and update/remove line
            cart.getLines().removeIf(line -> {
                if (request.getSku().equals(line.getSku())) {
                    if (request.getQty() <= 0) {
                        return true; // Remove line
                    } else {
                        line.setQty(request.getQty()); // Update quantity
                        return false;
                    }
                }
                return false;
            });

            // Choose appropriate transition
            String transition = request.getQty() <= 0 ? "remove_item" : "decrement_item";

            // Update cart with transition to recalculate totals
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Updated cart {} item {}: qty={}", cartId, request.getSku(), request.getQty());
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
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

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
            logger.error("Error opening checkout for cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update guest contact information
     * PUT /ui/cart/{cartId}/contact
     */
    @PutMapping("/{cartId}/contact")
    public ResponseEntity<EntityWithMetadata<Cart>> updateGuestContact(
            @PathVariable String cartId,
            @RequestBody Cart.CartGuestContact guestContact) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            cart.setGuestContact(guestContact);
            cart.setUpdatedAt(LocalDateTime.now());

            // Update cart without transition (stay in same state)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Updated guest contact for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating guest contact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Product getProductBySku(String sku) {
        try {
            ModelSpec productModelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            SimpleCondition skuCondition = new SimpleCondition()
                    .withJsonPath("$.sku")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(sku));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(skuCondition));

            List<EntityWithMetadata<Product>> products = entityService.search(
                    productModelSpec, condition, Product.class);

            return products.isEmpty() ? null : products.get(0).entity();
        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return null;
        }
    }

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
