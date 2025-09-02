package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ui/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    
    @Autowired
    private EntityService entityService;

    @PostMapping("/{cartId}")
    public ResponseEntity<Cart> addGuestContactToCart(
            @PathVariable String cartId,
            @RequestParam(required = false) String transition,
            @RequestBody CheckoutRequest request) {
        
        logger.info("Adding guest contact to cart: {}, transition: {}", cartId, transition);

        try {
            // Find cart
            Optional<Cart> cartOpt = findCartByCartId(cartId);
            if (cartOpt.isEmpty()) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartOpt.get();
            UUID cartEntityId = getCartEntityId(cartId);

            // Validate guest contact
            if (request.getGuestContact() == null) {
                logger.warn("Guest contact is required");
                return ResponseEntity.badRequest().build();
            }

            // Convert request to cart guest contact
            Cart.GuestContact guestContact = convertToCartGuestContact(request.getGuestContact());
            cart.setGuestContact(guestContact);

            // Update cart with transition
            String transitionToUse = transition != null ? transition : "checkout";
            EntityResponse<Cart> updatedCartResponse = entityService.update(cartEntityId, cart, transitionToUse);

            logger.info("Added guest contact to cart: {}", cartId);
            return ResponseEntity.ok(updatedCartResponse.getData());

        } catch (Exception e) {
            logger.error("Failed to add guest contact to cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Optional<Cart> findCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class,
                Cart.ENTITY_NAME,
                Cart.ENTITY_VERSION,
                condition,
                true
            );

            return cartResponse.map(EntityResponse::getData);
        } catch (Exception e) {
            logger.error("Failed to find cart by ID {}: {}", cartId, e.getMessage());
            return Optional.empty();
        }
    }

    private UUID getCartEntityId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class,
                Cart.ENTITY_NAME,
                Cart.ENTITY_VERSION,
                condition,
                true
            );

            return cartResponse.map(response -> response.getMetadata().getId()).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to get cart entity ID for {}: {}", cartId, e.getMessage());
            return null;
        }
    }

    private Cart.GuestContact convertToCartGuestContact(GuestContactRequest request) {
        Cart.GuestContact guestContact = new Cart.GuestContact();
        guestContact.setName(request.getName());
        guestContact.setEmail(request.getEmail());
        guestContact.setPhone(request.getPhone());

        if (request.getAddress() != null) {
            Cart.Address address = new Cart.Address();
            address.setLine1(request.getAddress().getLine1());
            address.setCity(request.getAddress().getCity());
            address.setPostcode(request.getAddress().getPostcode());
            address.setCountry(request.getAddress().getCountry());
            guestContact.setAddress(address);
        }

        return guestContact;
    }

    // Request DTOs
    public static class CheckoutRequest {
        private GuestContactRequest guestContact;

        public GuestContactRequest getGuestContact() { return guestContact; }
        public void setGuestContact(GuestContactRequest guestContact) { this.guestContact = guestContact; }
    }

    public static class GuestContactRequest {
        private String name;
        private String email;
        private String phone;
        private AddressRequest address;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public AddressRequest getAddress() { return address; }
        public void setAddress(AddressRequest address) { this.address = address; }
    }

    public static class AddressRequest {
        private String line1;
        private String city;
        private String postcode;
        private String country;

        public String getLine1() { return line1; }
        public void setLine1(String line1) { this.line1 = line1; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
    }
}
