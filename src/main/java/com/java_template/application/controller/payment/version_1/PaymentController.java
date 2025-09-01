package com.java_template.application.controller.payment.version_1;

import com.java_template.application.entity.payment.version_1.Payment;
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
 * PaymentController handles REST API endpoints for payment operations.
 * This controller is a proxy to the EntityService for Payment entities.
 */
@RestController
@RequestMapping("/ui/payment")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private EntityService entityService;

    /**
     * Start dummy payment process.
     * 
     * @param request Payment start request
     * @return Payment entity
     */
    @PostMapping("/start")
    public ResponseEntity<Payment> startPayment(@Valid @RequestBody StartPaymentRequest request) {
        logger.info("Starting payment for cart: {}", request.getCartId());

        try {
            // Validate cart exists and is in checkout state
            SearchConditionRequest cartCondition = SearchConditionRequest.group("and",
                Condition.of("cartId", "equals", request.getCartId()));
            
            var cartResponse = entityService.getFirstItemByCondition(Cart.class, cartCondition, false);
            if (cartResponse.isEmpty()) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            Cart cart = cartResponse.get().getData();
            String currentState = cartResponse.get().getMetadata().getState();
            
            // Validate cart is in checkout state
            if (!"CHECKING_OUT".equals(currentState)) {
                logger.warn("Cart is not in checkout state: {} (current state: {})", request.getCartId(), currentState);
                return ResponseEntity.badRequest().build();
            }
            
            // Validate cart has guest contact information
            if (cart.getGuestContact() == null) {
                logger.warn("Cart does not have guest contact information: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            // Create payment entity
            String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            
            Payment payment = new Payment();
            payment.setPaymentId(paymentId);
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());
            
            // Save payment with START_DUMMY_PAYMENT transition
            EntityResponse<Payment> savedPayment = entityService.save(payment);
            
            // Note: The AUTO_MARK_PAID transition should be scheduled by the processor
            // after ~3 seconds for demo purposes
            
            return ResponseEntity.ok(savedPayment.getData());
            
        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get payment status (for polling).
     * 
     * @param paymentId Payment identifier
     * @return Payment entity with current status
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPaymentStatus(@PathVariable String paymentId) {
        logger.info("Getting payment status: {}", paymentId);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("paymentId", "equals", paymentId));
            
            var paymentResponse = entityService.getFirstItemByCondition(Payment.class, condition, false);
            
            if (paymentResponse.isPresent()) {
                return ResponseEntity.ok(paymentResponse.get().getData());
            } else {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for starting payment.
     */
    public static class StartPaymentRequest {
        private String cartId;

        public String getCartId() {
            return cartId;
        }

        public void setCartId(String cartId) {
            this.cartId = cartId;
        }
    }
}
