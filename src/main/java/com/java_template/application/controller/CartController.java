package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ui/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;

    public CartController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Cart>> createCart(@RequestBody(required = false) Cart cart) {
        try {
            logger.info("Creating new cart");
            
            // Create new cart if not provided
            if (cart == null) {
                cart = new Cart();
            }
            
            // Initialize cart with default values
            if (cart.getCartId() == null) {
                cart.setCartId("cart-" + UUID.randomUUID().toString());
            }
            if (cart.getLines() == null) {
                cart.setLines(new ArrayList<>());
            }
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(Instant.now());
            cart.setUpdatedAt(Instant.now());
            
            // CRITICAL: Pass cart entity directly - it IS the payload
            EntityResponse<Cart> response = entityService.save(cart);
            logger.info("Cart created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCart(@PathVariable String cartId) {
        try {
            logger.info("Getting cart: {}", cartId);

            // Search for cart by cartId
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            if (cartResponse.isPresent()) {
                Cart cart = cartResponse.get().getData();
                logger.info("Found cart with {} items", cart.getTotalItems());
                return ResponseEntity.ok(cart);
            } else {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityResponse<Cart>> addItemToCart(
            @PathVariable String cartId, 
            @RequestBody Map<String, Object> request) {
        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");
            
            logger.info("Adding item to cart {}: sku={}, qty={}", cartId, sku, qty);

            if (sku == null || qty == null || qty <= 0) {
                logger.warn("Invalid add item request: sku={}, qty={}", sku, qty);
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Get product details
            Product product = getProductBySku(sku);
            if (product == null) {
                logger.warn("Product not found: {}", sku);
                return ResponseEntity.badRequest().build();
            }

            // Add or update line
            addOrUpdateCartLine(cart, product, qty);

            // Update cart with add_item transition
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            EntityResponse<Cart> response = entityService.update(cartTechnicalId, cart, "add_item");
            
            logger.info("Item added to cart {}: {}", cartId, sku);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding item to cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityResponse<Cart>> updateItemInCart(
            @PathVariable String cartId, 
            @RequestBody Map<String, Object> request) {
        try {
            String sku = (String) request.get("sku");
            Integer qty = (Integer) request.get("qty");
            
            logger.info("Updating item in cart {}: sku={}, qty={}", cartId, sku, qty);

            if (sku == null || qty == null || qty < 0) {
                logger.warn("Invalid update item request: sku={}, qty={}", sku, qty);
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Update or remove line
            if (qty == 0) {
                removeCartLine(cart, sku);
            } else {
                Product product = getProductBySku(sku);
                if (product == null) {
                    logger.warn("Product not found: {}", sku);
                    return ResponseEntity.badRequest().build();
                }
                updateCartLine(cart, product, qty);
            }

            // Update cart with update_item transition
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            EntityResponse<Cart> response = entityService.update(cartTechnicalId, cart, "update_item");
            
            logger.info("Item updated in cart {}: {}", cartId, sku);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating item in cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityResponse<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            logger.info("Opening checkout for cart: {}", cartId);

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Update cart with open_checkout transition
            UUID cartTechnicalId = getCartTechnicalId(cartId);
            EntityResponse<Cart> response = entityService.update(cartTechnicalId, cart, "open_checkout");
            
            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error opening checkout for cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting cart by cartId: {}", cartId, e);
            return null;
        }
    }

    private UUID getCartTechnicalId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(response -> response.getMetadata().getId()).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting cart technical ID: {}", cartId, e);
            return null;
        }
    }

    private Product getProductBySku(String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", skuCondition);

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);

            return productResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return null;
        }
    }

    private void addOrUpdateCartLine(Cart cart, Product product, Integer qty) {
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

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

    private void updateCartLine(Cart cart, Product product, Integer qty) {
        if (cart.getLines() == null) {
            return;
        }

        Optional<Cart.CartLine> existingLine = cart.getLines().stream()
            .filter(line -> product.getSku().equals(line.getSku()))
            .findFirst();

        if (existingLine.isPresent()) {
            existingLine.get().setQty(qty);
        }
    }

    private void removeCartLine(Cart cart, String sku) {
        if (cart.getLines() == null) {
            return;
        }

        cart.getLines().removeIf(line -> sku.equals(line.getSku()));
    }
}
