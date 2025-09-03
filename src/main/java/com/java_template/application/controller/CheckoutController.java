package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ui/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;

    public CheckoutController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/{cartId}")
    public ResponseEntity<EntityResponse<Cart>> setGuestContact(@PathVariable String cartId, @RequestBody Cart cart) {
        try {
            logger.info("Setting guest contact for cart: {}", cartId);

            // Find cart
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId, "cartId");

            if (cartResponse == null) {
                return ResponseEntity.notFound().build();
            }

            UUID cartEntityId = cartResponse.getMetadata().getId();

            // Update cart with guest contact - the cart entity already contains the guest contact data
            EntityResponse<Cart> response = entityService.update(cartEntityId, cart, null);
            logger.info("Guest contact set for cart: {}", cartId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error setting guest contact for cart: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
