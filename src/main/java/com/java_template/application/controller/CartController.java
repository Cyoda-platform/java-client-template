package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/ui/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    
    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Cart> createCart() {
        logger.info("Creating new cart");

        try {
            Cart cart = new Cart();
            cart.setCartId("cart-" + UUID.randomUUID().toString());
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityResponse<Cart> cartResponse = entityService.save(cart);
            
            logger.info("Created new cart: {}", cart.getCartId());
            return ResponseEntity.ok(cartResponse.getData());

        } catch (Exception e) {
            logger.error("Failed to create cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCart(@PathVariable String cartId) {
        logger.info("Getting cart: {}", cartId);

        try {
            Optional<Cart> cart = findCartByCartId(cartId);
            
            if (cart.isPresent()) {
                return ResponseEntity.ok(cart.get());
            } else {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Failed to get cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/lines")
    public ResponseEntity<Cart> addItemToCart(
            @PathVariable String cartId,
            @RequestParam(required = false) String transition,
            @RequestBody AddToCartRequest request) {
        
        logger.info("Adding item to cart {} - SKU: {}, qty: {}, transition: {}", 
                   cartId, request.getSku(), request.getQty(), transition);

        try {
            // Find cart
            Optional<Cart> cartOpt = findCartByCartId(cartId);
            if (cartOpt.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartOpt.get();
            UUID cartEntityId = getCartEntityId(cartId);

            // Find product to get details
            Product product = findProductBySku(request.getSku());
            if (product == null) {
                logger.warn("Product not found: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            // Add or update line item
            addOrUpdateCartLine(cart, product, request.getQty());

            // Update cart with transition
            String transitionToUse = transition != null ? transition : "add_item";
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartEntityId, cart, transitionToUse);

            logger.info("Added item to cart {}: {} x {}", cartId, request.getSku(), request.getQty());
            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Failed to add item to cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<Cart> updateCartItem(
            @PathVariable String cartId,
            @RequestParam(required = false) String transition,
            @RequestBody UpdateCartItemRequest request) {
        
        logger.info("Updating cart {} item - SKU: {}, qty: {}, transition: {}", 
                   cartId, request.getSku(), request.getQty(), transition);

        try {
            // Find cart
            Optional<Cart> cartOpt = findCartByCartId(cartId);
            if (cartOpt.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartOpt.get();
            UUID cartEntityId = getCartEntityId(cartId);

            // Update line item quantity (remove if qty = 0)
            updateCartLineQuantity(cart, request.getSku(), request.getQty());

            // Update cart with transition
            String transitionToUse = transition != null ? transition : "update_item";
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartEntityId, cart, transitionToUse);

            logger.info("Updated cart {} item: {} -> qty {}", cartId, request.getSku(), request.getQty());
            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Failed to update cart {} item: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<Cart> openCheckout(
            @PathVariable String cartId,
            @RequestParam(required = true) String transition) {
        
        logger.info("Opening checkout for cart: {}, transition: {}", cartId, transition);

        try {
            // Find cart
            Optional<Cart> cartOpt = findCartByCartId(cartId);
            if (cartOpt.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartOpt.get();
            UUID cartEntityId = getCartEntityId(cartId);

            // Update cart with checkout transition
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartEntityId, cart, transition);

            logger.info("Opened checkout for cart: {}", cartId);
            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Failed to open checkout for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Optional<Cart> findCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class,
                Cart.ENTITY_NAME,
                Cart.ENTITY_VERSION,
                condition,
                true
            );

            return cartResponse.map(EntityResponse::getData);
        } catch (Exception e) {
            logger.error("Failed to find cart by ID {}: {}", cartId, e.getMessage());
            return Optional.empty();
        }
    }

    private UUID getCartEntityId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class,
                Cart.ENTITY_NAME,
                Cart.ENTITY_VERSION,
                condition,
                true
            );

            return cartResponse.map(response -> response.getMetadata().getId()).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to get cart entity ID for {}: {}", cartId, e.getMessage());
            return null;
        }
    }

    private Product findProductBySku(String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(skuCondition));

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class,
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                condition,
                true
            );

            return productResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to find product by SKU {}: {}", sku, e.getMessage());
            return null;
        }
    }

    private void addOrUpdateCartLine(Cart cart, Product product, Integer qty) {
        // Find existing line
        Optional<Cart.CartLine> existingLine = cart.getLines().stream()
            .filter(line -> product.getSku().equals(line.getSku()))
            .findFirst();

        if (existingLine.isPresent()) {
            // Update existing line
            Cart.CartLine line = existingLine.get();
            line.setQty(line.getQty() + qty);
        } else {
            // Add new line
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(product.getSku());
            newLine.setName(product.getName());
            newLine.setPrice(product.getPrice());
            newLine.setQty(qty);
            cart.getLines().add(newLine);
        }
    }

    private void updateCartLineQuantity(Cart cart, String sku, Integer newQty) {
        cart.getLines().removeIf(line -> {
            if (sku.equals(line.getSku())) {
                if (newQty <= 0) {
                    return true; // Remove line
                } else {
                    line.setQty(newQty); // Update quantity
                    return false;
                }
            }
            return false;
        });
    }

    // Request DTOs
    public static class AddToCartRequest {
        private String sku;
        private Integer qty;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
    }

    public static class UpdateCartItemRequest {
        private String sku;
        private Integer qty;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
    }
}
