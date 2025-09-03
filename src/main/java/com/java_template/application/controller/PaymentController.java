package com.java_template.application.controller;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class StartPaymentRequest {
    private String cartId;

    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }
}

@RestController
@RequestMapping("/ui/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/start")
    public ResponseEntity<EntityResponse<Payment>> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            String cartId = request.getCartId();

            logger.info("Starting payment for cart: {}", cartId);

            if (cartId == null || cartId.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Find cart to get amount
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId, "cartId");

            if (cartResponse == null) {
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartResponse.getData();
            String cartState = cartResponse.getMetadata().getState();

            if (!"CHECKING_OUT".equals(cartState)) {
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(cartId);
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Save payment - the entity itself is the payload
            EntityResponse<Payment> response = entityService.save(payment);

            // Update cart to CONVERTED state
            UUID cartEntityId = cartResponse.getMetadata().getId();
            entityService.update(cartEntityId, cart, "CHECKOUT");

            logger.info("Payment started with ID: {}", payment.getPaymentId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<EntityResponse<Payment>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            logger.info("Getting payment status: {}", paymentId);

            // Find payment
            EntityResponse<Payment> response = entityService.findByBusinessId(Payment.class, paymentId, "paymentId");

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
