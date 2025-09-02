package com.java_template.application.controller.cart.version_1;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CartController manages shopping cart operations.
 */
@RestController
@RequestMapping("/ui/cart")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;

    public CartController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create new cart or return existing cart
     */
    @PostMapping
    public ResponseEntity<Cart> createCart(@RequestBody(required = false) Map<String, Object> requestBody) {
        logger.info("Creating new cart");

        try {
            // Create new cart
            String cartId = "cart_" + UUID.randomUUID().toString().replace("-", "");
            Cart cart = new Cart();
            cart.setCartId(cartId);
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            // Save cart
            EntityResponse<Cart> response = entityService.save(cart);
            Cart savedCart = response.getData();

            logger.info("Cart created successfully: cartId={}", savedCart.getCartId());
            return ResponseEntity.ok(savedCart);

        } catch (Exception e) {
            logger.error("Failed to create cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Add item to cart or increment quantity
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<Cart> addItemToCart(
            @PathVariable String cartId,
            @RequestParam(required = false) String transition,
            @RequestBody Map<String, Object> requestBody) {

        logger.info("Adding item to cart: cartId={}, transition={}", cartId, transition);

        try {
            // Get existing cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = cartResponse.getData();
            
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Prepare request data for processor
            Map<String, Object> processorData = new HashMap<>(requestBody);
            
            // Update cart with ADD_ITEM transition
            EntityResponse<Cart> updatedResponse = entityService.update(
                cartResponse.getId(),
                cart,
                "ADD_ITEM"
            );
            Cart updatedCart = updatedResponse.getData();

            logger.info("Item added to cart successfully: cartId={}, totalItems={}", 
                updatedCart.getCartId(), updatedCart.getTotalItems());
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Failed to add item to cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update item quantity or remove item (qty=0)
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<Cart> updateCartItem(
            @PathVariable String cartId,
            @RequestParam(required = false) String transition,
            @RequestBody Map<String, Object> requestBody) {

        logger.info("Updating cart item: cartId={}, transition={}", cartId, transition);

        try {
            // Get existing cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = cartResponse.getData();
            
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Update cart with UPDATE_ITEM transition
            EntityResponse<Cart> updatedResponse = entityService.update(
                cartResponse.getId(),
                cart,
                "UPDATE_ITEM"
            );
            Cart updatedCart = updatedResponse.getData();

            logger.info("Cart item updated successfully: cartId={}, totalItems={}", 
                updatedCart.getCartId(), updatedCart.getTotalItems());
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Failed to update cart item {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Move cart to checkout state
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<Cart> openCheckout(
            @PathVariable String cartId,
            @RequestParam String transition) {

        logger.info("Opening checkout for cart: cartId={}, transition={}", cartId, transition);

        if (!"OPEN_CHECKOUT".equals(transition)) {
            logger.warn("Invalid transition for open checkout: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = cartResponse.getData();
            
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Update cart with OPEN_CHECKOUT transition
            EntityResponse<Cart> updatedResponse = entityService.update(
                cartResponse.getId(),
                cart,
                "OPEN_CHECKOUT"
            );
            Cart updatedCart = updatedResponse.getData();

            logger.info("Checkout opened successfully: cartId={}", updatedCart.getCartId());
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Failed to open checkout for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cart details
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCart(@PathVariable String cartId) {
        logger.info("Getting cart details: cartId={}", cartId);

        try {
            EntityResponse<Cart> response = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = response.getData();
            
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found cart: cartId={}, totalItems={}, grandTotal={}", 
                cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());
            return ResponseEntity.ok(cart);

        } catch (Exception e) {
            logger.error("Failed to get cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
