package com.java_template.application.controller.checkout.version_1;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CheckoutController handles anonymous checkout process.
 */
@RestController
@RequestMapping("/ui/checkout")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;

    public CheckoutController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Attach guest contact information to cart
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<Cart> checkout(
            @PathVariable String cartId,
            @RequestParam String transition,
            @RequestBody Map<String, Object> requestBody) {

        logger.info("Processing checkout for cart: cartId={}, transition={}", cartId, transition);

        if (!"CHECKOUT".equals(transition)) {
            logger.warn("Invalid transition for checkout: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Validate guest contact information is provided
            @SuppressWarnings("unchecked")
            Map<String, Object> guestContactData = (Map<String, Object>) requestBody.get("guestContact");
            
            if (guestContactData == null) {
                logger.warn("Guest contact information is required for checkout");
                return ResponseEntity.badRequest().build();
            }

            // Get existing cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = cartResponse.getData();
            
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            // Create guest contact from request data
            Cart.GuestContact guestContact = createGuestContactFromData(guestContactData);
            cart.setGuestContact(guestContact);

            // Update cart with CHECKOUT transition
            EntityResponse<Cart> updatedResponse = entityService.update(
                cartResponse.getId(),
                cart,
                "CHECKOUT"
            );
            Cart updatedCart = updatedResponse.getData();

            logger.info("Checkout processed successfully: cartId={}, guestName={}", 
                updatedCart.getCartId(), guestContact.getName());
            return ResponseEntity.ok(updatedCart);

        } catch (Exception e) {
            logger.error("Failed to process checkout for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create guest contact from request data
     */
    private Cart.GuestContact createGuestContactFromData(Map<String, Object> guestContactData) {
        Cart.GuestContact guestContact = new Cart.GuestContact();
        
        guestContact.setName((String) guestContactData.get("name"));
        guestContact.setEmail((String) guestContactData.get("email"));
        guestContact.setPhone((String) guestContactData.get("phone"));

        @SuppressWarnings("unchecked")
        Map<String, Object> addressData = (Map<String, Object>) guestContactData.get("address");
        if (addressData != null) {
            Cart.GuestAddress address = new Cart.GuestAddress();
            address.setLine1((String) addressData.get("line1"));
            address.setCity((String) addressData.get("city"));
            address.setPostcode((String) addressData.get("postcode"));
            address.setCountry((String) addressData.get("country"));
            guestContact.setAddress(address);
        }

        return guestContact;
    }
}
