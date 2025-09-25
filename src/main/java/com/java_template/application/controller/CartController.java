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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart controller for shopping cart management.
 * Handles cart creation, item management, and checkout preparation.
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
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createCart() {
        logger.info("Creating new cart");

        try {
            // Create new cart
            Cart cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> savedCart = entityService.create(cart);
            logger.info("Created cart: {}", savedCart.entity().getCartId());

            return ResponseEntity.ok(savedCart);

        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get cart by ID
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        logger.info("Getting cart: {}", cartId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Cart.ENTITY_NAME);
            modelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found cart: {} with {} items", cartId, cartWithMetadata.entity().getTotalItems());
            return ResponseEntity.ok(cartWithMetadata);

        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Add item to cart or increment quantity
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {

        logger.info("Adding item to cart: {} - SKU: {}, Qty: {}", cartId, request.getSku(), request.getQty());

        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Get product details
            ModelSpec productModelSpec = new ModelSpec();
            productModelSpec.setName(Product.ENTITY_NAME);
            productModelSpec.setVersion(Product.ENTITY_VERSION);

            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, request.getSku(), "sku", Product.class);

            if (productWithMetadata == null) {
                logger.warn("Product not found: {}", request.getSku());
                return ResponseEntity.badRequest().build();
            }

            Product product = productWithMetadata.entity();

            // Check stock availability
            if (product.getQuantityAvailable() < request.getQty()) {
                logger.warn("Insufficient stock for SKU: {} - Available: {}, Requested: {}", 
                           request.getSku(), product.getQuantityAvailable(), request.getQty());
                return ResponseEntity.badRequest().build();
            }

            // Add or update cart line
            addOrUpdateCartLine(cart, product, request.getQty());

            // Update cart status to ACTIVE if it was NEW
            if ("NEW".equals(cart.getStatus())) {
                cart.setStatus("ACTIVE");
            }

            cart.setUpdatedAt(LocalDateTime.now());

            // Save cart with recalculation transition
            String transition = "NEW".equals(cartWithMetadata.entity().getStatus()) ? 
                    "create_on_first_add" : "add_item";
            
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Added item to cart: {} - Total items: {}", cartId, updatedCart.entity().getTotalItems());
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update item quantity in cart (set quantity or remove if qty=0)
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartItem(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {

        logger.info("Updating cart item: {} - SKU: {}, Qty: {}", cartId, request.getSku(), request.getQty());

        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Update or remove cart line
            if (request.getQty() == 0) {
                removeCartLine(cart, request.getSku());
            } else {
                setCartLineQuantity(cart, request.getSku(), request.getQty());
            }

            cart.setUpdatedAt(LocalDateTime.now());

            // Save cart with appropriate transition
            String transition = request.getQty() == 0 ? "remove_item" : "decrement_item";
            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, transition);

            logger.info("Updated cart item: {} - Total items: {}", cartId, updatedCart.entity().getTotalItems());
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error updating cart item: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Open checkout (set cart to CHECKING_OUT status)
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        logger.info("Opening checkout for cart: {}", cartId);

        try {
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            cart.setStatus("CHECKING_OUT");
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> updatedCart = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, "open_checkout");

            logger.info("Opened checkout for cart: {}", cartId);
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Add or update cart line with product details
     */
    private void addOrUpdateCartLine(Cart cart, Product product, Integer quantity) {
        List<Cart.CartLine> lines = cart.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
            cart.setLines(lines);
        }

        // Find existing line
        Cart.CartLine existingLine = lines.stream()
                .filter(line -> product.getSku().equals(line.getSku()))
                .findFirst()
                .orElse(null);

        if (existingLine != null) {
            // Update existing line
            existingLine.setQty(existingLine.getQty() + quantity);
        } else {
            // Add new line
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(product.getSku());
            newLine.setName(product.getName());
            newLine.setPrice(product.getPrice());
            newLine.setQty(quantity);
            lines.add(newLine);
        }
    }

    /**
     * Set cart line quantity to specific value
     */
    private void setCartLineQuantity(Cart cart, String sku, Integer quantity) {
        List<Cart.CartLine> lines = cart.getLines();
        if (lines != null) {
            lines.stream()
                    .filter(line -> sku.equals(line.getSku()))
                    .findFirst()
                    .ifPresent(line -> line.setQty(quantity));
        }
    }

    /**
     * Remove cart line by SKU
     */
    private void removeCartLine(Cart cart, String sku) {
        List<Cart.CartLine> lines = cart.getLines();
        if (lines != null) {
            lines.removeIf(line -> sku.equals(line.getSku()));
        }
    }

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
