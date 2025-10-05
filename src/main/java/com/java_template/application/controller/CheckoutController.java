package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * ABOUTME: Checkout controller providing REST endpoints for anonymous
 * checkout process including guest contact information management.
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
     * Process anonymous checkout with guest contact information
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> processCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found for checkout: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.warn("Cannot checkout empty cart: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact information
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                logger.warn("Invalid guest contact information for checkout: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Validate address information
            if (request.getGuestContact().getAddress() == null ||
                request.getGuestContact().getAddress().getLine1() == null ||
                request.getGuestContact().getAddress().getLine1().trim().isEmpty() ||
                request.getGuestContact().getAddress().getCity() == null ||
                request.getGuestContact().getAddress().getCity().trim().isEmpty() ||
                request.getGuestContact().getAddress().getPostcode() == null ||
                request.getGuestContact().getAddress().getPostcode().trim().isEmpty() ||
                request.getGuestContact().getAddress().getCountry() == null ||
                request.getGuestContact().getAddress().getCountry().trim().isEmpty()) {
                logger.warn("Invalid address information for checkout: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Update cart with guest contact information
            cart.setGuestContact(request.getGuestContact());
            cart.setUpdatedAt(LocalDateTime.now());

            // Update cart without transition (stay in current state)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Processed checkout for cart {} with guest: {}", 
                       cartId, request.getGuestContact().getName());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing checkout", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get checkout information for a cart
     * GET /ui/checkout/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<CheckoutInfoResponse> getCheckoutInfo(@PathVariable String cartId) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            String cartState = cartWithMetadata.metadata().getState();

            CheckoutInfoResponse response = new CheckoutInfoResponse();
            response.setCartId(cart.getCartId());
            response.setCartState(cartState);
            response.setTotalItems(cart.getTotalItems());
            response.setGrandTotal(cart.getGrandTotal());
            response.setGuestContact(cart.getGuestContact());
            response.setCanCheckout(cart.getLines() != null && !cart.getLines().isEmpty());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting checkout info for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate checkout readiness
     * GET /ui/checkout/{cartId}/validate
     */
    @GetMapping("/{cartId}/validate")
    public ResponseEntity<CheckoutValidationResponse> validateCheckout(@PathVariable String cartId) {
        try {
            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            CheckoutValidationResponse response = new CheckoutValidationResponse();
            response.setCartId(cart.getCartId());

            // Validate cart has items
            boolean hasItems = cart.getLines() != null && !cart.getLines().isEmpty();
            response.setHasItems(hasItems);

            // Validate guest contact
            boolean hasValidContact = cart.getGuestContact() != null &&
                    cart.getGuestContact().getName() != null &&
                    !cart.getGuestContact().getName().trim().isEmpty();
            response.setHasValidContact(hasValidContact);

            // Validate address
            boolean hasValidAddress = cart.getGuestContact() != null &&
                    cart.getGuestContact().getAddress() != null &&
                    cart.getGuestContact().getAddress().getLine1() != null &&
                    !cart.getGuestContact().getAddress().getLine1().trim().isEmpty() &&
                    cart.getGuestContact().getAddress().getCity() != null &&
                    !cart.getGuestContact().getAddress().getCity().trim().isEmpty() &&
                    cart.getGuestContact().getAddress().getPostcode() != null &&
                    !cart.getGuestContact().getAddress().getPostcode().trim().isEmpty() &&
                    cart.getGuestContact().getAddress().getCountry() != null &&
                    !cart.getGuestContact().getAddress().getCountry().trim().isEmpty();
            response.setHasValidAddress(hasValidAddress);

            // Overall validation
            response.setCanProceedToPayment(hasItems && hasValidContact && hasValidAddress);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error validating checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Getter
    @Setter
    public static class CheckoutRequest {
        private Cart.CartGuestContact guestContact;
    }

    @Getter
    @Setter
    public static class CheckoutInfoResponse {
        private String cartId;
        private String cartState;
        private Integer totalItems;
        private Double grandTotal;
        private Cart.CartGuestContact guestContact;
        private Boolean canCheckout;
    }

    @Getter
    @Setter
    public static class CheckoutValidationResponse {
        private String cartId;
        private Boolean hasItems;
        private Boolean hasValidContact;
        private Boolean hasValidAddress;
        private Boolean canProceedToPayment;
    }
}
