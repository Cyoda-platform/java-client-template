package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;

    public CartController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Cart>> createCart(@RequestBody Cart cart) {
        try {
            EntityResponse<Cart> response = entityService.save(cart);
            logger.info("Cart created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating cart", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Cart>> getCart(@PathVariable UUID id) {
        try {
            EntityResponse<Cart> response = entityService.getItem(id, Cart.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving cart with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/cartId/{cartId}")
    public ResponseEntity<EntityResponse<Cart>> getCartByCartId(@PathVariable String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> response = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving cart with cartId: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Cart>>> getAllCarts() {
        try {
            List<EntityResponse<Cart>> carts = entityService.findAll(Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION);
            return ResponseEntity.ok(carts);
        } catch (Exception e) {
            logger.error("Error retrieving all carts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Cart>> updateCart(
            @PathVariable UUID id, 
            @RequestBody Cart cart,
            @RequestParam(required = false) String transition) {
        try {
            EntityResponse<Cart> response = entityService.update(id, cart, transition);
            logger.info("Cart updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating cart with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCart(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Cart deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting cart with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<EntityResponse<Cart>> activateCart(@PathVariable UUID id) {
        try {
            EntityResponse<Cart> cartResponse = entityService.getItem(id, Cart.class);
            Cart cart = cartResponse.getData();
            EntityResponse<Cart> response = entityService.update(id, cart, "ACTIVATE");
            logger.info("Cart activated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating cart with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/add-item")
    public ResponseEntity<EntityResponse<Cart>> addItemToCart(@PathVariable UUID id, @RequestBody Cart cart) {
        try {
            EntityResponse<Cart> response = entityService.update(id, cart, "ADD_ITEM");
            logger.info("Item added to cart with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding item to cart with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/update-item")
    public ResponseEntity<EntityResponse<Cart>> updateItemInCart(@PathVariable UUID id, @RequestBody Cart cart) {
        try {
            EntityResponse<Cart> response = entityService.update(id, cart, "UPDATE_ITEM");
            logger.info("Item updated in cart with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating item in cart with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/remove-item")
    public ResponseEntity<EntityResponse<Cart>> removeItemFromCart(@PathVariable UUID id, @RequestBody Cart cart) {
        try {
            EntityResponse<Cart> response = entityService.update(id, cart, "REMOVE_ITEM");
            logger.info("Item removed from cart with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error removing item from cart with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<EntityResponse<Cart>> checkoutCart(@PathVariable UUID id, @RequestBody Cart cart) {
        try {
            EntityResponse<Cart> response = entityService.update(id, cart, "CHECKOUT");
            logger.info("Cart checkout initiated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking out cart with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
