```java
package com.java_template.application.controller;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ui/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createPayment(@RequestBody Map<String, Object> request) {
        logger.info("Creating payment with request: {}", request);

        try {
            String cartId = (String) request.get("cartId");
            Double amount = null;

            // Handle both Double and Integer for amount
            Object amountObj = request.get("amount");
            if (amountObj instanceof Double) {
                amount = (Double) amountObj;
            } else if (amountObj instanceof Integer) {
                amount = ((Integer) amountObj).doubleValue();
            }

            if (cartId == null || amount == null || amount <= 0) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
            }

            // Create payment entity
            Payment payment = new Payment();
            payment.setCartId(cartId);
            payment.setAmount(amount);
            payment.setProvider("DUMMY");

            // Add payment with CREATE_DUMMY transition
            return entityService.addItem(Payment.ENTITY_NAME, Payment.ENTITY_VERSION, payment)
                .thenApply(paymentEntityId -> {
                    try {
                        // Get the created payment
                        return getPaymentResponse(paymentEntityId);
                    } catch (Exception e) {
                        logger.error("Error getting created payment: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                }).exceptionally(throwable -> {
                    logger.error("Error creating payment: {}", throwable.getMessage(), throwable);
                    return ResponseEntity.internalServerError().build();
                });

        } catch (Exception e) {
            logger.error("Error in createPayment: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @GetMapping("/{paymentId}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getPayment(@PathVariable String paymentId) {
        logger.info("Getting payment: {}", paymentId);

        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", paymentIdCondition);

            return entityService.getFirstItemByCondition(
                Payment.ENTITY_NAME,
                Payment.ENTITY_VERSION,
                condition,
                true
            ).thenApply(optionalPayload -> {
                if (optionalPayload.isPresent()) {
                    try {
                        Payment payment = objectMapper.convertValue(optionalPayload.get().getData(), Payment.class);
                        String state = optionalPayload.get().getMetadata().getState();
                        UUID entityId = optionalPayload.get().getMetadata().getId();

                        Map<String, Object> response = createPaymentResponse(payment, state, entityId);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        logger.error("Error converting payment data: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                } else {
                    return ResponseEntity.notFound().<Map<String, Object>>build();
                }
            }).exceptionally(throwable -> {
                logger.error("Error getting payment {}: {}", paymentId, throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in getPayment: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @GetMapping("/cart/{cartId}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getPaymentByCartId(@PathVariable String cartId) {
        logger.info("Getting payment by cart ID: {}", cartId);

        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

            return entityService.getFirstItemByCondition(
                Payment.ENTITY_NAME,
                Payment.ENTITY_VERSION,
                condition,
                true
            ).thenApply(optionalPayload -> {
                if (optionalPayload.isPresent()) {
                    try {
                        Payment payment = objectMapper.convertValue(optionalPayload.get().getData(), Payment.class);
                        String state = optionalPayload.get().getMetadata().getState();
                        UUID entityId = optionalPayload.get().getMetadata().getId();

                        Map<String, Object> response = createPaymentResponse(payment, state, entityId);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        logger.error("Error converting payment data: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                } else {
                    return ResponseEntity.notFound().<Map<String, Object>>build();
                }
            }).exceptionally(throwable -> {
                logger.error("Error getting payment by cart ID {}: {}", cartId, throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in getPaymentByCartId: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    private ResponseEntity<Map<String, Object>> getPaymentResponse(UUID paymentEntityId) {
        try {
            return entityService.getItem(paymentEntityId).thenApply(optionalPayload -> {
                if (optionalPayload.isPresent()) {
                    try {
                        Payment payment = objectMapper.convertValue(optionalPayload.get().getData(), Payment.class);
                        String state = optionalPayload.get().getMetadata().getState();

                        Map<String, Object> response = createPaymentResponse(payment, state, paymentEntityId);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        logger.error("Error converting payment data: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                } else {
                    return ResponseEntity.notFound().<Map<String, Object>>build();
                }
            }).join();
        } catch (Exception e) {
            logger.error("Error getting payment response: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> createPaymentResponse(Payment payment, String state, UUID entityId) {
        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", payment.getPaymentId());
        response.put("cartId", payment.getCartId());
        response.put("amount", payment.getAmount());
        response.put("provider", payment.getProvider());
        response.put("state", state);
        response.put("entityId", entityId.toString());
        response.put("createdAt", payment.getCreatedAt());
        response.put("updatedAt", payment.getUpdatedAt());

        return response;
    }
}
```