package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/ui/cart")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Cart> createCart() {
        logger.info("Creating new cart");

        try {
            Cart cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            
            Instant now = Instant.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);

            EntityResponse<Cart> cartResponse = entityService.save(cart);
            return ResponseEntity.ok(cartResponse.getData());

        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/lines")
    public ResponseEntity<Cart> addItemToCart(
            @PathVariable String cartId,
            @RequestParam(required = false) String transitionName,
            @RequestBody Map<String, Object> request) {

        logger.info("Adding item to cart: {}", cartId);

        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            if (sku == null || qty == null || qty <= 0) {
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.notFound().build();
            }

            // Get product details
            Product product = getProductBySku(sku);
            if (product == null) {
                return ResponseEntity.badRequest().build();
            }

            // Check if item already exists in cart
            boolean itemExists = false;
            for (Cart.CartLine line : cart.getLines()) {
                if (sku.equals(line.getSku())) {
                    line.setQty(line.getQty() + qty);
                    itemExists = true;
                    break;
                }
            }

            // Add new line if item doesn't exist
            if (!itemExists) {
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(sku);
                newLine.setName(product.getName());
                newLine.setPrice(product.getPrice());
                newLine.setQty(qty);
                cart.getLines().add(newLine);
            }

            // Update cart with transition
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            String transition = transitionName != null ? transitionName : "ADD_ITEM";
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartTechnicalId, cart, transition);

            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<Cart> updateCartItem(
            @PathVariable String cartId,
            @RequestParam(required = false) String transitionName,
            @RequestBody Map<String, Object> request) {

        logger.info("Updating cart item in cart: {}", cartId);

        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");

            if (sku == null || qty == null) {
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.notFound().build();
            }

            // Update or remove item
            cart.getLines().removeIf(line -> {
                if (sku.equals(line.getSku())) {
                    if (qty <= 0) {
                        return true; // Remove item
                    } else {
                        line.setQty(qty); // Update quantity
                        return false;
                    }
                }
                return false;
            });

            // Update cart with transition
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            String transition = transitionName != null ? transitionName : "UPDATE_ITEM";
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartTechnicalId, cart, transition);

            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Error updating cart item in cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<Cart> openCheckout(
            @PathVariable String cartId,
            @RequestParam(required = false) String transitionName) {

        logger.info("Opening checkout for cart: {}", cartId);

        try {
            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.notFound().build();
            }

            // Update cart with checkout transition
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            String transition = transitionName != null ? transitionName : "OPEN_CHECKOUT";
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartTechnicalId, cart, transition);

            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCart(@PathVariable String cartId) {
        logger.info("Getting cart: {}", cartId);

        try {
            Cart cart = getCartByCartId(cartId);
            if (cart != null) {
                return ResponseEntity.ok(cart);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving cart by cartId: {}", cartId, e);
            return null;
        }
    }

    private UUID getCartTechnicalId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(response -> response.getMetadata().getId()).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving cart technical ID: {}", cartId, e);
            return null;
        }
    }

    private Product getProductBySku(String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(skuCondition));

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);

            return productResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving product by SKU: {}", sku, e);
            return null;
        }
    }
}
