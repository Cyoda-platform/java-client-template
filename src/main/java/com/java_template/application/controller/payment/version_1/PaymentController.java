package com.java_template.application.controller.payment.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Payment entity. All business logic is executed in workflows.
 */
@RestController
@RequestMapping("/entity/payment/v1")
@Tag(name = "Payment", description = "Payment entity proxy endpoints (v1)")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Payment", description = "Create a Payment entity. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPayment(
            @RequestBody PaymentCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic validation
            if (request.getCartId() == null || request.getCartId().isBlank()) {
                throw new IllegalArgumentException("cartId is required");
            }
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                throw new IllegalArgumentException("userId is required");
            }
            if (request.getAmount() == null) {
                throw new IllegalArgumentException("amount is required");
            }

            // Map to entity (controller must not contain business logic)
            Payment entity = new Payment();
            // create technical natural id for entity
            String id = UUID.randomUUID().toString();
            entity.setId(id);
            entity.setCartId(request.getCartId());
            entity.setUserId(request.getUserId());
            entity.setAmount(request.getAmount());
            entity.setCreatedAt(request.getCreatedAt() != null ? request.getCreatedAt() : java.time.Instant.now().toString());
            entity.setStatus(request.getStatus() != null ? request.getStatus() : "PENDING");
            entity.setOrderId(request.getOrderId());
            entity.setApprovedAt(request.getApprovedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Payment.ENTITY_NAME,
                    Payment.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create payment: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while creating payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while creating payment", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Payment by technicalId", description = "Retrieve a Payment entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPaymentById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("Payment not found");
            }
            JsonNode dataNode = (JsonNode) dataPayload.getData();
            PaymentResponse resp = objectMapper.treeToValue(dataNode, PaymentResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid get request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving payment", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "List Payments", description = "Retrieve all Payment entities (paged support not implemented; returns all).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = PaymentResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = {"!cartId", "!userId"})
    public ResponseEntity<?> listPayments() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture =
                    entityService.getItems(Payment.ENTITY_NAME, Payment.ENTITY_VERSION, null, null, null);
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<PaymentResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    PaymentResponse resp = objectMapper.treeToValue(data, PaymentResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while listing payments", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing payments", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while listing payments", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Search Payments", description = "Search Payments by cartId and/or userId using simple field conditions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = PaymentResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchPayments(
            @Parameter(description = "Filter by cartId") @RequestParam(value = "cartId", required = false) String cartId,
            @Parameter(description = "Filter by userId") @RequestParam(value = "userId", required = false) String userId) {
        try {
            SearchConditionRequest condition = null;
            List<Condition> conditions = new ArrayList<>();
            if (cartId != null && !cartId.isBlank()) {
                conditions.add(Condition.of("$.cartId", "EQUALS", cartId));
            }
            if (userId != null && !userId.isBlank()) {
                conditions.add(Condition.of("$.userId", "EQUALS", userId));
            }
            if (!conditions.isEmpty()) {
                condition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            }
            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Payment.ENTITY_NAME,
                    Payment.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<PaymentResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    PaymentResponse resp = objectMapper.treeToValue(data, PaymentResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while searching payments", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching payments", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while searching payments", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Update Payment", description = "Update a Payment entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updatePayment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @RequestBody PaymentUpdateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Map to entity for update. Controller does not implement business logic.
            Payment entity = new Payment();
            entity.setId(request.getId() != null ? request.getId() : technicalId);
            entity.setCartId(request.getCartId());
            entity.setUserId(request.getUserId());
            entity.setAmount(request.getAmount());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setStatus(request.getStatus());
            entity.setOrderId(request.getOrderId());
            entity.setApprovedAt(request.getApprovedAt());

            CompletableFuture<java.util.UUID> updatedIdFuture = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while updating payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while updating payment", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Delete Payment", description = "Delete a Payment entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deletePayment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while deleting payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while deleting payment", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // ===== DTOs =====

    @Data
    @Schema(name = "PaymentCreateRequest", description = "Request to create a Payment")
    public static class PaymentCreateRequest {
        @Schema(description = "Order id (nullable until order created)", example = "order-1")
        private String orderId;

        @Schema(description = "Cart id", required = true, example = "cart-1")
        private String cartId;

        @Schema(description = "User id", required = true, example = "u-123")
        private String userId;

        @Schema(description = "Amount", required = true, example = "19.98")
        private Double amount;

        @Schema(description = "Status", example = "PENDING")
        private String status;

        @Schema(description = "Created at timestamp (ISO-8601). If absent controller will set current time.", example = "2025-08-28T12:00:00Z")
        private String createdAt;

        @Schema(description = "Approved at timestamp (ISO-8601)", example = "2025-08-28T12:00:03Z")
        private String approvedAt;
    }

    @Data
    @Schema(name = "PaymentUpdateRequest", description = "Request to update a Payment")
    public static class PaymentUpdateRequest {
        @Schema(description = "Entity id (optional, will use path technicalId if absent)", example = "payment-1")
        private String id;

        @Schema(description = "Order id (nullable until order created)", example = "order-1")
        private String orderId;

        @Schema(description = "Cart id", example = "cart-1")
        private String cartId;

        @Schema(description = "User id", example = "u-123")
        private String userId;

        @Schema(description = "Amount", example = "19.98")
        private Double amount;

        @Schema(description = "Status", example = "APPROVED")
        private String status;

        @Schema(description = "Created at timestamp (ISO-8601).", example = "2025-08-28T12:00:00Z")
        private String createdAt;

        @Schema(description = "Approved at timestamp (ISO-8601)", example = "2025-08-28T12:00:03Z")
        private String approvedAt;
    }

    @Data
    @Schema(name = "PaymentResponse", description = "Payment response payload")
    public static class PaymentResponse {
        @Schema(description = "Entity id", example = "payment-1")
        private String id;

        @Schema(description = "Order id (nullable until order created)", example = "order-1")
        private String orderId;

        @Schema(description = "Cart id", example = "cart-1")
        private String cartId;

        @Schema(description = "User id", example = "u-123")
        private String userId;

        @Schema(description = "Amount", example = "19.98")
        private Double amount;

        @Schema(description = "Status", example = "PENDING")
        private String status;

        @Schema(description = "Created at timestamp (ISO-8601).", example = "2025-08-28T12:00:00Z")
        private String createdAt;

        @Schema(description = "Approved at timestamp (ISO-8601)", example = "2025-08-28T12:00:03Z")
        private String approvedAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}