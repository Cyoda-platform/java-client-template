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

/**
 * Checkout Controller for anonymous checkout process
 * Handles guest contact information and checkout flow
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
    public ResponseEntity<EntityWithMetadata<Cart>> submitCheckout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            // Find cart
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in ACTIVE status
            if (!"ACTIVE".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in ACTIVE status: {}", cartId, cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart has items
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.warn("Cart {} has no items", cartId);
                return ResponseEntity.badRequest().build();
            }

            // Validate guest contact information
            if (request.getGuestContact() == null || 
                request.getGuestContact().getName() == null || 
                request.getGuestContact().getName().trim().isEmpty()) {
                logger.warn("Guest contact name is required for checkout");
                return ResponseEntity.badRequest().build();
            }

            if (request.getGuestContact().getAddress() == null ||
                !isValidAddress(request.getGuestContact().getAddress())) {
                logger.warn("Valid address is required for checkout");
                return ResponseEntity.badRequest().build();
            }

            // Update cart with guest contact information
            Cart.GuestContact guestContact = new Cart.GuestContact();
            guestContact.setName(request.getGuestContact().getName());
            guestContact.setEmail(request.getGuestContact().getEmail());
            guestContact.setPhone(request.getGuestContact().getPhone());

            Cart.Address address = new Cart.Address();
            address.setLine1(request.getGuestContact().getAddress().getLine1());
            address.setCity(request.getGuestContact().getAddress().getCity());
            address.setPostcode(request.getGuestContact().getAddress().getPostcode());
            address.setCountry(request.getGuestContact().getAddress().getCountry());
            guestContact.setAddress(address);

            cart.setGuestContact(guestContact);

            // Update cart (no transition - just update guest contact)
            EntityWithMetadata<Cart> response = entityService.update(
                    cartWithMetadata.metadata().getId(), cart, null);

            logger.info("Checkout information submitted for cart {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting checkout for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get checkout information for cart
     * GET /ui/checkout/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<CheckoutResponse> getCheckoutInfo(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            CheckoutResponse response = new CheckoutResponse();
            response.setCartId(cart.getCartId());
            response.setStatus(cart.getStatus());
            response.setTotalItems(cart.getTotalItems());
            response.setGrandTotal(cart.getGrandTotal());
            
            if (cart.getGuestContact() != null) {
                response.setGuestContact(mapGuestContact(cart.getGuestContact()));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting checkout info for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate address has required fields
     */
    private boolean isValidAddress(CheckoutRequest.Address address) {
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    /**
     * Map cart guest contact to response DTO
     */
    private CheckoutResponse.GuestContact mapGuestContact(Cart.GuestContact cartGuestContact) {
        CheckoutResponse.GuestContact responseGuestContact = new CheckoutResponse.GuestContact();
        responseGuestContact.setName(cartGuestContact.getName());
        responseGuestContact.setEmail(cartGuestContact.getEmail());
        responseGuestContact.setPhone(cartGuestContact.getPhone());
        
        if (cartGuestContact.getAddress() != null) {
            CheckoutResponse.Address responseAddress = new CheckoutResponse.Address();
            responseAddress.setLine1(cartGuestContact.getAddress().getLine1());
            responseAddress.setCity(cartGuestContact.getAddress().getCity());
            responseAddress.setPostcode(cartGuestContact.getAddress().getPostcode());
            responseAddress.setCountry(cartGuestContact.getAddress().getCountry());
            responseGuestContact.setAddress(responseAddress);
        }
        
        return responseGuestContact;
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContact guestContact;

        @Getter
        @Setter
        public static class GuestContact {
            private String name;
            private String email;
            private String phone;
            private Address address;
        }

        @Getter
        @Setter
        public static class Address {
            private String line1;
            private String city;
            private String postcode;
            private String country;
        }
    }

    @Getter
    @Setter
    public static class CheckoutResponse {
        private String cartId;
        private String status;
        private Integer totalItems;
        private Double grandTotal;
        private GuestContact guestContact;

        @Getter
        @Setter
        public static class GuestContact {
            private String name;
            private String email;
            private String phone;
            private Address address;
        }

        @Getter
        @Setter
        public static class Address {
            private String line1;
            private String city;
            private String postcode;
            private String country;
        }
    }
}
