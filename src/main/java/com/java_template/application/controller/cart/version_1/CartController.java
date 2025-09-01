package com.java_template.application.controller.cart.version_1;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * CartController handles REST API endpoints for cart operations.
 * This controller is a proxy to the EntityService for Cart entities.
 */
@RestController
@RequestMapping("/ui/cart")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private EntityService entityService;

    /**
     * Create new cart or return existing cart.
     * 
     * @return Cart entity
     */
    @PostMapping
    public ResponseEntity<Cart> createCart() {
        logger.info("Creating new cart");

        try {
            // Create new cart with generated ID
            String cartId = "cart_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            
            Cart cart = new Cart();
            cart.setCartId(cartId);
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(BigDecimal.ZERO);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            
            EntityResponse<Cart> savedCart = entityService.save(cart);
            return ResponseEntity.ok(savedCart.getData());
            
        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Add or increment item in cart.
     * 
     * @param cartId Cart identifier
     * @param transition Workflow transition name (optional)
     * @param request Add item request
     * @return Updated Cart entity
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<Cart> addItemToCart(
            @PathVariable String cartId,
            @RequestParam(required = false) String transition,
            @Valid @RequestBody AddItemRequest request) {
        
        logger.info("Adding item to cart: cartId={}, sku={}, qty={}", cartId, request.getSku(), request.getQty());

        try {
            // Get existing cart
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("cartId", "equals", cartId));
            
            var cartResponse = entityService.getFirstItemByCondition(Cart.class, condition, false);
            if (cartResponse.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }
            
            Cart cart = cartResponse.get().getData();
            UUID entityId = cartResponse.get().getMetadata().getId();
            
            // Get product details
            SearchConditionRequest productCondition = SearchConditionRequest.group("and",
                Condition.of("sku", "equals", request.getSku()));
            
            var productResponse = entityService.getFirstItemByCondition(Product.class, productCondition, false);
            if (productResponse.isEmpty()) {
                logger.warn("Product not found: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }
            
            Product product = productResponse.get().getData();
            
            // Check stock availability
            if (product.getQuantityAvailable() < request.getQty()) {
                logger.warn("Insufficient stock for product: {}", request.getSku());
                return ResponseEntity.status(409).build(); // Conflict
            }
            
            // Add or update cart line
            addOrUpdateCartLine(cart, product, request.getQty());
            
            // Recalculate totals
            recalculateCartTotals(cart);
            cart.setUpdatedAt(LocalDateTime.now());
            
            // Update cart with transition if provided
            EntityResponse<Cart> updatedCart = entityService.update(entityId, cart, transition);
            return ResponseEntity.ok(updatedCart.getData());
            
        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update or remove item in cart.
     * 
     * @param cartId Cart identifier
     * @param transition Workflow transition name (optional)
     * @param request Update item request
     * @return Updated Cart entity
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<Cart> updateCartItem(
            @PathVariable String cartId,
            @RequestParam(required = false) String transition,
            @Valid @RequestBody UpdateItemRequest request) {
        
        logger.info("Updating cart item: cartId={}, sku={}, qty={}", cartId, request.getSku(), request.getQty());

        try {
            // Get existing cart
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("cartId", "equals", cartId));
            
            var cartResponse = entityService.getFirstItemByCondition(Cart.class, condition, false);
            if (cartResponse.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }
            
            Cart cart = cartResponse.get().getData();
            UUID entityId = cartResponse.get().getMetadata().getId();
            
            // Update or remove cart line
            if (request.getQty() <= 0) {
                removeCartLine(cart, request.getSku());
            } else {
                updateCartLine(cart, request.getSku(), request.getQty());
            }
            
            // Recalculate totals
            recalculateCartTotals(cart);
            cart.setUpdatedAt(LocalDateTime.now());
            
            // Update cart with transition if provided
            EntityResponse<Cart> updatedCart = entityService.update(entityId, cart, transition);
            return ResponseEntity.ok(updatedCart.getData());
            
        } catch (Exception e) {
            logger.error("Error updating cart item", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Transition cart to checkout state.
     * 
     * @param cartId Cart identifier
     * @param transition Workflow transition name (default: "OPEN_CHECKOUT")
     * @return Updated Cart entity with CHECKING_OUT status
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<Cart> openCheckout(
            @PathVariable String cartId,
            @RequestParam(defaultValue = "OPEN_CHECKOUT") String transition) {
        
        logger.info("Opening checkout for cart: {}", cartId);

        try {
            // Get existing cart
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("cartId", "equals", cartId));
            
            var cartResponse = entityService.getFirstItemByCondition(Cart.class, condition, false);
            if (cartResponse.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }
            
            Cart cart = cartResponse.get().getData();
            UUID entityId = cartResponse.get().getMetadata().getId();
            
            // Validate cart has items
            if (cart.getLines().isEmpty()) {
                logger.warn("Cannot checkout empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }
            
            cart.setUpdatedAt(LocalDateTime.now());
            
            // Update cart with checkout transition
            EntityResponse<Cart> updatedCart = entityService.update(entityId, cart, transition);
            return ResponseEntity.ok(updatedCart.getData());
            
        } catch (Exception e) {
            logger.error("Error opening checkout for cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get cart details.
     * 
     * @param cartId Cart identifier
     * @return Cart entity
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCart(@PathVariable String cartId) {
        logger.info("Getting cart: {}", cartId);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("cartId", "equals", cartId));
            
            var cartResponse = entityService.getFirstItemByCondition(Cart.class, condition, false);
            
            if (cartResponse.isPresent()) {
                return ResponseEntity.ok(cartResponse.get().getData());
            } else {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Helper methods
    private void addOrUpdateCartLine(Cart cart, Product product, Integer qty) {
        Cart.CartLine existingLine = cart.getLines().stream()
            .filter(line -> line.getSku().equals(product.getSku()))
            .findFirst()
            .orElse(null);
        
        if (existingLine != null) {
            existingLine.setQty(existingLine.getQty() + qty);
        } else {
            Cart.CartLine newLine = new Cart.CartLine(
                product.getSku(),
                product.getName(),
                product.getPrice(),
                qty
            );
            cart.getLines().add(newLine);
        }
    }

    private void updateCartLine(Cart cart, String sku, Integer newQty) {
        cart.getLines().stream()
            .filter(line -> line.getSku().equals(sku))
            .findFirst()
            .ifPresent(line -> line.setQty(newQty));
    }

    private void removeCartLine(Cart cart, String sku) {
        cart.getLines().removeIf(line -> line.getSku().equals(sku));
    }

    private void recalculateCartTotals(Cart cart) {
        int totalItems = cart.getLines().stream()
            .mapToInt(Cart.CartLine::getQty)
            .sum();
        
        BigDecimal grandTotal = cart.getLines().stream()
            .map(Cart.CartLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
    }

    // Request DTOs
    public static class AddItemRequest {
        private String sku;
        private Integer qty;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
    }

    public static class UpdateItemRequest {
        private String sku;
        private Integer qty;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
    }
}
