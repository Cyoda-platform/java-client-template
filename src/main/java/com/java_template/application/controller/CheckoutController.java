package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Checkout Controller for OMS anonymous checkout flow
 * Handles guest contact information and checkout preparation
 */
@RestController
@RequestMapping("/ui/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;

    public CheckoutController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Submit checkout with guest contact information
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<UUID> submitCheckout(
            @PathVariable String cartId, 
            @RequestBody CheckoutRequest request) {
        
        logger.info("Submitting checkout for cart: {}", cartId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Cart.ENTITY_NAME);
            modelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate cart is in correct state
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT state: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                logger.warn("Invalid guest contact information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Validate address
            Cart.GuestAddress address = request.getGuestContact().getAddress();
            if (address == null || 
                address.getLine1() == null || address.getLine1().trim().isEmpty() ||
                address.getCity() == null || address.getCity().trim().isEmpty() ||
                address.getPostcode() == null || address.getPostcode().trim().isEmpty() ||
                address.getCountry() == null || address.getCountry().trim().isEmpty()) {
                logger.warn("Invalid address information for cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Update cart with guest contact information
            cart.setGuestContact(request.getGuestContact());
            cart.setStatus("CONVERTED");

            EntityWithMetadata<Cart> updatedCart = entityService.updateByBusinessId(cart, "cartId", "checkout");
            UUID technicalId = updatedCart.metadata().getId();

            logger.info("Checkout submitted for cart: {} (technical: {})", cartId, technicalId);
            return ResponseEntity.ok(technicalId);

        } catch (Exception e) {
            logger.error("Error submitting checkout for cart: {}", cartId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for checkout submission
     */
    @Data
    public static class CheckoutRequest {
        private Cart.GuestContact guestContact;
    }
}
