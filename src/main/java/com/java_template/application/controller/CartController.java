package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Cart controller providing shopping cart management endpoints
 * for cart creation, line item management, and checkout initiation.
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
        try {
            // Create new cart
            Cart cart = new Cart();
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.entity().getCartId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Cart", e);
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
            logger.error("Error getting Cart by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get cart by cart ID (business identifier)
     * GET /ui/cart/business/{cartId}
     */
    @GetMapping("/business/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCartByCartId(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> response = entityService.findByBusinessIdOrNull(
                    modelSpec, cartId, "cartId", Cart.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Cart by cartId: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add item to cart (or increment quantity if exists)
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<?> addItemToCart(
            @PathVariable String cartId,
            @RequestBody AddItemRequest request) {
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart status
            if (!"NEW".equals(cart.getStatus()) && !"ACTIVE".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cannot modify cart in status: " + cart.getStatus()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Find product to get current price and name
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessIdOrNull(
                    productModelSpec, request.getSku(), "sku", Product.class);

            if (productWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Product not found: " + request.getSku()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Product product = productWithMetadata.entity();

            // Check inventory
            if (product.getQuantityAvailable() < request.getQty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient inventory. Available: " + product.getQuantityAvailable()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Add or update cart line
            addOrUpdateCartLine(cart, request.getSku(), product.getName(), product.getPrice(), request.getQty());

            // Set cart to ACTIVE if it was NEW
            if ("NEW".equals(cart.getStatus())) {
                cart.setStatus("ACTIVE");
            }

            // Update cart with recalculation transition
            String transition = "NEW".equals(cartWithMetadata.entity().getStatus()) ? "create_on_first_add" : "add_item";
            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Added item {} to cart {}", request.getSku(), cartId);
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
    public ResponseEntity<?> updateCartLine(
            @PathVariable String cartId,
            @RequestBody UpdateItemRequest request) {
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart status
            if (!"ACTIVE".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cannot modify cart in status: " + cart.getStatus()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Update or remove cart line
            if (request.getQty() <= 0) {
                removeCartLine(cart, request.getSku());
            } else {
                updateCartLineQuantity(cart, request.getSku(), request.getQty());
            }

            // Update cart with recalculation transition
            String transition = request.getQty() <= 0 ? "remove_item" : "decrement_item";
            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Updated item {} in cart {} to quantity {}", request.getSku(), cartId, request.getQty());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating cart line", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Open checkout (set cart to CHECKING_OUT status)
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<?> openCheckout(@PathVariable String cartId) {
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart status
            if (!"ACTIVE".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cannot checkout cart in status: " + cart.getStatus()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cannot checkout empty cart"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Update cart status
            cart.setStatus("CHECKING_OUT");
            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, "open_checkout");
            
            logger.info("Opened checkout for cart {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error opening checkout", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add guest contact information to cart
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/checkout/{cartId}")
    public ResponseEntity<?> addGuestContact(
            @PathVariable String cartId,
            @RequestBody GuestContactRequest request) {
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart must be in CHECKING_OUT status. Current status: " + cart.getStatus()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Set guest contact
            cart.setGuestContact(request.getGuestContact());
            cart.setUpdatedAt(LocalDateTime.now());

            // Update cart (no transition - stay in same state)
            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, null);
            
            logger.info("Added guest contact to cart {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding guest contact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private void addOrUpdateCartLine(Cart cart, String sku, String name, Double price, Integer qty) {
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        // Find existing line
        Cart.CartLine existingLine = cart.getLines().stream()
                .filter(line -> sku.equals(line.getSku()))
                .findFirst()
                .orElse(null);

        if (existingLine != null) {
            // Update existing line
            existingLine.setQty(existingLine.getQty() + qty);
            existingLine.setLineTotal(existingLine.getPrice() * existingLine.getQty());
        } else {
            // Add new line
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(sku);
            newLine.setName(name);
            newLine.setPrice(price);
            newLine.setQty(qty);
            newLine.setLineTotal(price * qty);
            cart.getLines().add(newLine);
        }
    }

    private void updateCartLineQuantity(Cart cart, String sku, Integer qty) {
        if (cart.getLines() != null) {
            cart.getLines().stream()
                    .filter(line -> sku.equals(line.getSku()))
                    .findFirst()
                    .ifPresent(line -> {
                        line.setQty(qty);
                        line.setLineTotal(line.getPrice() * qty);
                    });
        }
    }

    private void removeCartLine(Cart cart, String sku) {
        if (cart.getLines() != null) {
            cart.getLines().removeIf(line -> sku.equals(line.getSku()));
        }
    }

    // Request DTOs

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

    @Getter
    @Setter
    public static class GuestContactRequest {
        private Cart.GuestContact guestContact;
    }
}
