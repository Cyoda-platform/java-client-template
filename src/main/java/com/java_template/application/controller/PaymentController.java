package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PaymentController - Dummy Payment API
 * Endpoints: POST /ui/payment/start, GET /ui/payment/{paymentId}
 */
@RestController
@RequestMapping("/ui/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Start dummy payment
     * POST /ui/payment/start
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Get cart to retrieve amount
            ModelSpec cartSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            SimpleCondition cartIdCondition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(request.getCartId()));

            GroupCondition cartCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cartIdCondition));

            List<EntityWithMetadata<Cart>> cartResults = entityService.search(
                    cartSpec, cartCondition, Cart.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (cartResults.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResults.get(0).entity();

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            
            logger.info("Payment created with ID: {}", response.entity().getPaymentId());

            PaymentStartResponse paymentResponse = new PaymentStartResponse();
            paymentResponse.setPaymentId(response.entity().getPaymentId());
            
            return ResponseEntity.ok(paymentResponse);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to start payment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get payment status
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            
            SimpleCondition paymentIdCondition = new SimpleCondition()
                    .withJsonPath("$.paymentId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(paymentId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(paymentIdCondition));

            List<EntityWithMetadata<Payment>> results = entityService.search(
                    modelSpec, condition, Payment.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Retrieved payment with ID: {}", paymentId);
            return ResponseEntity.ok(results.get(0));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve payment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Getter
    @Setter
    public static class PaymentStartRequest {
        private String cartId;
    }

    @Getter
    @Setter
    public static class PaymentStartResponse {
        private String paymentId;
    }
}

