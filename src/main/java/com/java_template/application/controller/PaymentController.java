package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.payment.version_1.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payment API", description = "APIs for Payment entity (event-driven)")
public class PaymentController {
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Payment", description = "Persist a Payment and start its workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPayment(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Payment payload") @RequestBody PaymentRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Payment payment = new Payment();
            payment.setPaymentId(request.getPaymentId());
            payment.setOrderId(request.getOrderId());
            payment.setMethod(request.getMethod());
            payment.setAmount(request.getAmount());
            payment.setCurrency(request.getCurrency());
            payment.setStatus(request.getStatus());
            payment.setProviderResponse(request.getProviderResponse());
            payment.setCreatedAt(request.getCreatedAt());

            if (!payment.isValid()) throw new IllegalArgumentException("Invalid payment payload");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    payment
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request to create payment", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating payment", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Payment by technicalId", description = "Retrieve the persisted Payment by internal technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPayment(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request to get payment", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving payment", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "PaymentRequest", description = "Payment request payload")
    public static class PaymentRequest {
        private String paymentId;
        private String orderId;
        private String method;
        private Double amount;
        private String currency;
        private String status;
        private String providerResponse;
        private String createdAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the generated technical id")
    public static class TechnicalIdResponse {
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
