package com.java_template.application.controller.payment.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/ui/payment")
@Tag(name = "Payment Controller", description = "Proxy controller for Payment entity (version 1)")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Start Payment", description = "Create a Payment entity (persist INITIATED payment event). Returns only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/start")
    public ResponseEntity<?> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            if (request == null || request.getCartId() == null || request.getCartId().isBlank()) {
                throw new IllegalArgumentException("cartId is required");
            }

            // Build Payment entity (controller acts as a simple proxy/persistor).
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(request.getAmount() != null ? request.getAmount() : 0.0);
            payment.setProvider(request.getProvider() != null ? request.getProvider() : "DUMMY");
            payment.setStatus(request.getStatus() != null ? request.getStatus() : "INITIATED");
            String now = Instant.now().toString();
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Payment.ENTITY_NAME,
                    Payment.ENTITY_VERSION,
                    payment
            );

            java.util.UUID entityId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in startPayment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in startPayment", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in startPayment", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Payment", description = "Retrieve Payment entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Payment.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPayment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(404).body("Payment not found");
            }

            Payment payment = objectMapper.treeToValue(dataPayload.getData(), Payment.class);
            return ResponseEntity.ok(payment);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in getPayment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPayment", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getPayment", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Schema(name = "StartPaymentRequest", description = "Request to start a payment. Minimal payload contains cartId.")
    public static class StartPaymentRequest {
        @Schema(description = "Business cart technical id", example = "t-cart-123")
        private String cartId;

        @Schema(description = "Payment amount (optional)", example = "12.50")
        private Double amount;

        @Schema(description = "Payment provider (optional)", example = "DUMMY")
        private String provider;

        @Schema(description = "Initial status (optional)", example = "INITIATED")
        private String status;

        public StartPaymentRequest() {}

        public String getCartId() {
            return cartId;
        }

        public void setCartId(String cartId) {
            this.cartId = cartId;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    @Schema(name = "TechnicalIdResponse", description = "Response containing only technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "t-payment-456")
        private String technicalId;

        public TechnicalIdResponse() {}

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}