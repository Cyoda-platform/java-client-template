package com.java_template.application.controller.cart.version_1;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public CompletableFuture<ResponseEntity<Cart>> createCart(@RequestBody Cart cart) {
        logger.info("Creating cart");

        return entityService.create(cart)
            .thenApply(createdCart -> {
                logger.info("Cart created successfully with ID: {}", createdCart.getCartId());
                return ResponseEntity.ok(createdCart);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to create cart: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/{cartId}")
    public CompletableFuture<ResponseEntity<Cart>> getCart(@PathVariable String cartId) {
        logger.info("Retrieving cart with ID: {}", cartId);

        return entityService.findById(Cart.class, cartId)
            .thenApply(cart -> {
                if (cart != null) {
                    logger.info("Cart found with ID: {}", cartId);
                    return ResponseEntity.ok(cart);
                } else {
                    logger.warn("Cart not found with ID: {}", cartId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve cart: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PostMapping("/{cartId}/items")
    public CompletableFuture<ResponseEntity<Cart>> addItemToCart(@PathVariable String cartId, @RequestBody Cart.Line item) {
        logger.info("Adding item to cart {}: SKU {}, Qty {}", cartId, item.getSku(), item.getQty());

        return entityService.findById(Cart.class, cartId)
            .thenCompose(cart -> {
                if (cart == null) {
                    // Create new cart if it doesn't exist
                    cart = new Cart();
                    cart.setCartId(cartId);
                    cart.setLines(new java.util.ArrayList<>());
                }

                // Add or update item in cart
                boolean itemExists = false;
                for (Cart.Line line : cart.getLines()) {
                    if (line.getSku().equals(item.getSku())) {
                        line.setQty(line.getQty() + item.getQty());
                        itemExists = true;
                        break;
                    }
                }

                if (!itemExists) {
                    cart.getLines().add(item);
                }

                return entityService.update(cart);
            })
            .thenApply(updatedCart -> {
                logger.info("Item added to cart {} successfully", cartId);
                return ResponseEntity.ok(updatedCart);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to add item to cart: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PutMapping("/{cartId}/items/{sku}")
    public CompletableFuture<ResponseEntity<Cart>> updateCartItem(@PathVariable String cartId, @PathVariable String sku, @RequestBody Cart.Line item) {
        logger.info("Updating cart {} item {}: new qty {}", cartId, sku, item.getQty());

        return entityService.findById(Cart.class, cartId)
            .thenCompose(cart -> {
                if (cart == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Update item quantity
                for (Cart.Line line : cart.getLines()) {
                    if (line.getSku().equals(sku)) {
                        line.setQty(item.getQty());
                        break;
                    }
                }

                return entityService.update(cart);
            })
            .thenApply(updatedCart -> {
                if (updatedCart != null) {
                    logger.info("Cart item updated successfully");
                    return ResponseEntity.ok(updatedCart);
                } else {
                    logger.warn("Cart not found with ID: {}", cartId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to update cart item: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @DeleteMapping("/{cartId}/items/{sku}")
    public CompletableFuture<ResponseEntity<Cart>> removeItemFromCart(@PathVariable String cartId, @PathVariable String sku) {
        logger.info("Removing item {} from cart {}", sku, cartId);

        return entityService.findById(Cart.class, cartId)
            .thenCompose(cart -> {
                if (cart == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Remove item from cart
                cart.getLines().removeIf(line -> line.getSku().equals(sku));

                return entityService.update(cart);
            })
            .thenApply(updatedCart -> {
                if (updatedCart != null) {
                    logger.info("Item removed from cart successfully");
                    return ResponseEntity.ok(updatedCart);
                } else {
                    logger.warn("Cart not found with ID: {}", cartId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to remove item from cart: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PostMapping("/{cartId}/checkout")
    public CompletableFuture<ResponseEntity<Cart>> checkoutCart(@PathVariable String cartId, @RequestBody Cart.GuestContact guestContact) {
        logger.info("Checking out cart {} with guest contact: {}", cartId, guestContact.getName());

        return entityService.findById(Cart.class, cartId)
            .thenCompose(cart -> {
                if (cart == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Attach guest contact and trigger checkout
                cart.setGuestContact(guestContact);

                return entityService.update(cart);
            })
            .thenApply(updatedCart -> {
                if (updatedCart != null) {
                    logger.info("Cart checkout initiated successfully");
                    return ResponseEntity.ok(updatedCart);
                } else {
                    logger.warn("Cart not found with ID: {}", cartId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to checkout cart: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @DeleteMapping("/{cartId}")
    public CompletableFuture<ResponseEntity<Void>> deleteCart(@PathVariable String cartId) {
        logger.info("Deleting cart with ID: {}", cartId);

        return entityService.delete(Cart.class, cartId)
            .thenApply(deleted -> {
                if (deleted) {
                    logger.info("Cart deleted successfully with ID: {}", cartId);
                    return ResponseEntity.noContent().build();
                } else {
                    logger.warn("Cart not found for deletion with ID: {}", cartId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to delete cart: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
}