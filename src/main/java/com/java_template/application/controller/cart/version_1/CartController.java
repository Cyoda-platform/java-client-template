package com.java_template.application.controller.cart.version_1;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Cart> createCart(@RequestBody Cart cart) {
        logger.info("Creating cart with ID: {}", cart.getCartId());

        try {
            Cart createdCart = entityService.create(cart);
            logger.info("Cart created successfully with ID: {}", createdCart.getCartId());
            return ResponseEntity.ok(createdCart);
        } catch (Exception e) {
            logger.error("Failed to create cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCart(@PathVariable String cartId) {
        logger.info("Retrieving cart with ID: {}", cartId);

        try {
            Cart cart = entityService.findById(Cart.class, cartId);
            if (cart != null) {
                logger.info("Cart found with ID: {}", cartId);
                return ResponseEntity.ok(cart);
            } else {
                logger.warn("Cart not found with ID: {}", cartId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/items/{sku}")
    public ResponseEntity<Cart> addItemToCart(@PathVariable String cartId, @PathVariable String sku, @RequestBody Cart.Line item) {
        logger.info("Adding item {} to cart {}", sku, cartId);

        try {
            Cart cart = entityService.findById(Cart.class, cartId);
            if (cart == null) {
                logger.warn("Cart not found with ID: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Add or update item in cart
            boolean found = false;
            for (Cart.Line line : cart.getLines()) {
                if (line.getSku().equals(sku)) {
                    line.setQty(line.getQty() + item.getQty());
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                item.setSku(sku);
                cart.getLines().add(item);
            }

            Cart updatedCart = entityService.update(cart);
            logger.info("Item added to cart successfully");
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Failed to add item to cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{cartId}/items/{sku}")
    public ResponseEntity<Cart> updateCartItem(@PathVariable String cartId, @PathVariable String sku, @RequestBody Cart.Line item) {
        logger.info("Updating item {} in cart {}", sku, cartId);

        try {
            Cart cart = entityService.findById(Cart.class, cartId);
            if (cart == null) {
                logger.warn("Cart not found with ID: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Update item quantity
            for (Cart.Line line : cart.getLines()) {
                if (line.getSku().equals(sku)) {
                    line.setQty(item.getQty());
                    break;
                }
            }

            Cart updatedCart = entityService.update(cart);
            logger.info("Cart item updated successfully");
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Failed to update cart item: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{cartId}/items/{sku}")
    public ResponseEntity<Cart> removeItemFromCart(@PathVariable String cartId, @PathVariable String sku) {
        logger.info("Removing item {} from cart {}", sku, cartId);

        try {
            Cart cart = entityService.findById(Cart.class, cartId);
            if (cart == null) {
                logger.warn("Cart not found with ID: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Remove item from cart
            cart.getLines().removeIf(line -> line.getSku().equals(sku));

            Cart updatedCart = entityService.update(cart);
            logger.info("Item removed from cart successfully");
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Failed to remove item from cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{cartId}/checkout")
    public ResponseEntity<Cart> checkoutCart(@PathVariable String cartId, @RequestBody Cart.GuestContact guestContact) {
        logger.info("Checking out cart with ID: {}", cartId);

        try {
            Cart cart = entityService.findById(Cart.class, cartId);
            if (cart == null) {
                logger.warn("Cart not found with ID: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Attach guest contact and trigger checkout
            cart.setGuestContact(guestContact);

            Cart updatedCart = entityService.update(cart);
            logger.info("Cart checkout initiated successfully");
            return ResponseEntity.ok(updatedCart);
        } catch (Exception e) {
            logger.error("Failed to checkout cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{cartId}")
    public ResponseEntity<Void> deleteCart(@PathVariable String cartId) {
        logger.info("Deleting cart with ID: {}", cartId);

        try {
            boolean deleted = entityService.delete(Cart.class, cartId);
            if (deleted) {
                logger.info("Cart deleted successfully with ID: {}", cartId);
                return ResponseEntity.noContent().build();
            } else {
                logger.warn("Cart not found for deletion with ID: {}", cartId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to delete cart: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
