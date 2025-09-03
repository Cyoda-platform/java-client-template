package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
    public ResponseEntity<EntityResponse<Cart>> createOrGetCart(@RequestBody Cart cart) {
        try {
            logger.info("Creating or getting cart with first item - Cart ID: {}", cart.getCartId());

            // Save cart with CREATE_ON_FIRST_ADD transition
            // The entity itself is the payload - no need for manual payload manipulation
            EntityResponse<Cart> response = entityService.save(cart);
            logger.info("Cart created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityResponse<Cart>> addItemToCart(@PathVariable String cartId, @RequestBody Cart cart) {
        try {
            logger.info("Adding item to cart: {}", cartId);

            // Find cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId, "cartId");

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            UUID cartEntityId = cartResponse.getMetadata().getId();

            // Update cart with ADD_ITEM transition - the cart entity itself contains the new line item data
            EntityResponse<Cart> response = entityService.update(cartEntityId, cart, "ADD_ITEM");
            logger.info("Item added to cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityResponse<Cart>> updateCartItem(@PathVariable String cartId, @RequestBody Cart cart) {
        try {
            logger.info("Updating cart item: {}", cartId);

            // Find cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId, "cartId");

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            UUID cartEntityId = cartResponse.getMetadata().getId();

            // Update cart with UPDATE_ITEM transition - the cart entity contains the updated line item data
            EntityResponse<Cart> response = entityService.update(cartEntityId, cart, "UPDATE_ITEM");

            logger.info("Cart item updated: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating cart item: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityResponse<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            logger.info("Opening checkout for cart: {}", cartId);

            // Find cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId, "cartId");

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Cart existingCart = cartResponse.getData();
            UUID cartEntityId = cartResponse.getMetadata().getId();

            // Update cart with OPEN_CHECKOUT transition
            EntityResponse<Cart> response = entityService.update(cartEntityId, existingCart, "OPEN_CHECKOUT");
            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error opening checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<EntityResponse<Cart>> getCart(@PathVariable String cartId) {
        try {
            logger.info("Getting cart details: {}", cartId);

            // Find cart
            EntityResponse<Cart> response = entityService.findByBusinessId(Cart.class, cartId, "cartId");

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
