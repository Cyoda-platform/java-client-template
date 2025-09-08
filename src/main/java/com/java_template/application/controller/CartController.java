package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CartController - UI-facing REST controller for cart operations
 * 
 * This controller provides:
 * - Cart creation and retrieval
 * - Adding/updating/removing cart lines
 * - Cart totals recalculation
 * - Checkout preparation
 * 
 * Endpoints:
 * - POST /ui/cart - Create or return cart
 * - GET /ui/cart/{cartId} - Get cart details
 * - POST /ui/cart/{cartId}/lines - Add/increment line
 * - PATCH /ui/cart/{cartId}/lines - Update/decrement line
 * - POST /ui/cart/{cartId}/open-checkout - Prepare for checkout
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
     * Create or return cart (on first add, initialize NEW→ACTIVE)
     * POST /ui/cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        try {
            Cart cart = new Cart();
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8));
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            
            LocalDateTime now = LocalDateTime.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.entity().getCartId());
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
            ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);

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
     * Add/increment cart line
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addCartLine(
            @PathVariable String cartId,
            @RequestBody AddLineRequest request) {
        try {
            // Get cart
            EntityWithMetadata<Cart> cartWithMetadata = getCartByCartId(cartId);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Get product details
            Product product = getProductBySku(request.getSku());
            if (product == null) {
                return ResponseEntity.badRequest().build();
            }

            // Add or update line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Update cart with ADD_ITEM transition
            EntityWithMetadata<Cart> response = entityService.update(
                cartWithMetadata.metadata().getId(), cart, "ADD_ITEM");
            
            logger.info("Added {} x {} to cart {}", request.getQty(), request.getSku(), cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding cart line", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update/decrement cart line (remove if qty=0)
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @RequestBody UpdateLineRequest request) {
        try {
            // Get cart
            EntityWithMetadata<Cart> cartWithMetadata = getCartByCartId(cartId);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Update or remove line
            if (request.getQty() == 0) {
                removeCartLine(cart, request.getSku());
            } else {
                Product product = getProductBySku(request.getSku());
                if (product == null) {
                    return ResponseEntity.badRequest().build();
                }
                setCartLineQuantity(cart, product, request.getQty());
            }

            // Update cart with UPDATE_ITEM transition
            EntityWithMetadata<Cart> response = entityService.update(
                cartWithMetadata.metadata().getId(), cart, "UPDATE_ITEM");
            
            logger.info("Updated cart line {} to qty {} in cart {}", 
                       request.getSku(), request.getQty(), cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating cart line", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout (set CHECKING_OUT status)
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            // Get cart
            EntityWithMetadata<Cart> cartWithMetadata = getCartByCartId(cartId);
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Update cart with OPEN_CHECKOUT transition
            EntityWithMetadata<Cart> response = entityService.update(
                cartWithMetadata.metadata().getId(), cart, "OPEN_CHECKOUT");
            
            logger.info("Opened checkout for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private EntityWithMetadata<Cart> getCartByCartId(String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);

            return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
        } catch (Exception e) {
            logger.error("Error finding cart by ID: {}", cartId, e);
            return null;
        }
    }

    private Product getProductBySku(String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec()
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
                modelSpec, condition, Product.class);

            return products.isEmpty() ? null : products.get(0).entity();
        } catch (Exception e) {
            logger.error("Error finding product by SKU: {}", sku, e);
            return null;
        }
    }

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
            existingLine.setLineTotal(existingLine.getPrice() * existingLine.getQty());
        } else {
            // Add new line
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(product.getSku());
            newLine.setName(product.getName());
            newLine.setPrice(product.getPrice());
            newLine.setQty(qty);
            newLine.setLineTotal(product.getPrice() * qty);
            cart.getLines().add(newLine);
        }
    }

    private void setCartLineQuantity(Cart cart, Product product, Integer qty) {
        if (cart.getLines() == null) {
            return;
        }

        Cart.CartLine existingLine = cart.getLines().stream()
            .filter(line -> product.getSku().equals(line.getSku()))
            .findFirst()
            .orElse(null);

        if (existingLine != null) {
            existingLine.setQty(qty);
            existingLine.setLineTotal(existingLine.getPrice() * qty);
        }
    }

    private void removeCartLine(Cart cart, String sku) {
        if (cart.getLines() == null) {
            return;
        }

        cart.getLines().removeIf(line -> sku.equals(line.getSku()));
    }

    // Request DTOs

    @Getter
    @Setter
    public static class AddLineRequest {
        private String sku;
        private Integer qty;
    }

    @Getter
    @Setter
    public static class UpdateLineRequest {
        private String sku;
        private Integer qty;
    }
}
