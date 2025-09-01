package com.java_template.application.controller.checkout.version_1;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CheckoutController handles REST API endpoints for checkout operations.
 * This controller is a proxy to the EntityService for Cart entities during checkout.
 */
@RestController
@RequestMapping("/ui/checkout")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    @Autowired
    private EntityService entityService;

    /**
     * Attach guest contact information to cart.
     * 
     * @param cartId Cart identifier
     * @param request Checkout request with guest contact information
     * @return Updated Cart entity with guest contact information
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<Cart> attachGuestContact(
            @PathVariable String cartId,
            @Valid @RequestBody CheckoutRequest request) {
        
        logger.info("Attaching guest contact to cart: {}", cartId);

        try {
            // Get existing cart
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("cartId", "equals", cartId));
            
            var cartResponse = entityService.getFirstItemByCondition(Cart.class, condition, false);
            if (cartResponse.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }
            
            Cart cart = cartResponse.get().getData();
            UUID entityId = cartResponse.get().getMetadata().getId();
            
            // Validate cart is in checkout state
            String currentState = cartResponse.get().getMetadata().getState();
            if (!"CHECKING_OUT".equals(currentState)) {
                logger.warn("Cart is not in checkout state: {} (current state: {})", cartId, currentState);
                return ResponseEntity.badRequest().build();
            }
            
            // Validate cart has items
            if (cart.getLines().isEmpty()) {
                logger.warn("Cannot checkout empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }
            
            // Attach guest contact information
            cart.setGuestContact(request.getGuestContact());
            cart.setUpdatedAt(LocalDateTime.now());
            
            // Update cart (no transition needed, just updating data)
            EntityResponse<Cart> updatedCart = entityService.update(entityId, cart, null);
            return ResponseEntity.ok(updatedCart.getData());
            
        } catch (Exception e) {
            logger.error("Error attaching guest contact to cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for checkout operations.
     */
    public static class CheckoutRequest {
        @Valid
        private Cart.GuestContact guestContact;

        public Cart.GuestContact getGuestContact() {
            return guestContact;
        }

        public void setGuestContact(Cart.GuestContact guestContact) {
            this.guestContact = guestContact;
        }
    }
}
