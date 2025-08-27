package com.java_template.application.controller.payment.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.UUID;

@RestController
@RequestMapping("/entity/Payment/v1")
@Tag(name = "Payment", description = "CRUD operations for Payment entity (proxy to EntityService)")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Payment", description = "Create a single Payment entity. Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Payment create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreatePaymentRequest.class)))
            @RequestBody CreatePaymentRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getCartId() == null || request.getCartId().isBlank())
                throw new IllegalArgumentException("cartId is required");
            if (request.getAmount() == null)
                throw new IllegalArgumentException("amount is required");
            if (request.getProvider() == null || request.getProvider().isBlank())
                throw new IllegalArgumentException("provider is required");

            Payment payment = new Payment();
            payment.setCartId(request.getCartId());
            payment.setAmount(request.getAmount());
            payment.setProvider(request.getProvider());
            payment.setStatus(request.getStatus());
            payment.setPaymentId(request.getPaymentId());
            payment.setCreatedAt(request.getCreatedAt());
            payment.setUpdatedAt(request.getUpdatedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    payment
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create Payment: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating Payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating Payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error when creating Payment", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Create multiple Payments", description = "Create multiple Payment entities. Returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createPaymentsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Payment create requests", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreatePaymentRequest.class))))
            @RequestBody List<CreatePaymentRequest> requests) {
        try {
            if (requests == null || requests.isEmpty())
                throw new IllegalArgumentException("Request body must contain at least one payment");

            List<Payment> payments = new ArrayList<>();
            for (CreatePaymentRequest r : requests) {
                if (r == null) throw new IllegalArgumentException("Null item in request list");
                if (r.getCartId() == null || r.getCartId().isBlank())
                    throw new IllegalArgumentException("cartId is required for each payment");
                if (r.getAmount() == null)
                    throw new IllegalArgumentException("amount is required for each payment");
                if (r.getProvider() == null || r.getProvider().isBlank())
                    throw new IllegalArgumentException("provider is required for each payment");

                Payment p = new Payment();
                p.setCartId(r.getCartId());
                p.setAmount(r.getAmount());
                p.setProvider(r.getProvider());
                p.setStatus(r.getStatus());
                p.setPaymentId(r.getPaymentId());
                p.setCreatedAt(r.getCreatedAt());
                p.setUpdatedAt(r.getUpdatedAt());
                payments.add(p);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    payments
            );
            List<UUID> ids = idsFuture.get();
            List<String> strIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            return ResponseEntity.ok(new TechnicalIdsResponse(strIds));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid batch create request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating Payments batch", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating Payments batch", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error when creating Payments batch", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Payment by technicalId", description = "Retrieve a single Payment entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPayment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    tid
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getPayment: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when getting Payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when getting Payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error when getting Payment", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get all Payments", description = "Retrieve all Payment entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPayments() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when getting all Payments", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when getting all Payments", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error when getting all Payments", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Search Payments by condition", description = "Retrieve Payments filtered by a simple condition group")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPayments(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching Payments", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when searching Payments", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error when searching Payments", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Update Payment", description = "Update a Payment entity by technicalId. Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PatchMapping("/{technicalId}")
    public ResponseEntity<?> updatePayment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Payment update request", required = true,
                    content = @Content(schema = @Schema(implementation = UpdatePaymentRequest.class)))
            @RequestBody UpdatePaymentRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID tid = UUID.fromString(technicalId);

            Payment payment = new Payment();
            // Controller is a dumb proxy — only map provided fields
            payment.setCartId(request.getCartId());
            payment.setAmount(request.getAmount());
            payment.setProvider(request.getProvider());
            payment.setStatus(request.getStatus());
            payment.setPaymentId(request.getPaymentId());
            payment.setCreatedAt(request.getCreatedAt());
            payment.setUpdatedAt(request.getUpdatedAt());

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    tid,
                    payment
            );
            UUID updated = updatedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updated.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update Payment: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating Payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when updating Payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error when updating Payment", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Delete Payment", description = "Delete a Payment entity by technicalId. Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePayment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Payment.ENTITY_NAME,
                    String.valueOf(Payment.ENTITY_VERSION),
                    tid
            );
            UUID deleted = deletedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deleted.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deletePayment: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting Payment", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when deleting Payment", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error when deleting Payment", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Static DTO classes for requests/responses

    @Data
    @Schema(name = "CreatePaymentRequest", description = "Request to create a Payment")
    public static class CreatePaymentRequest {
        @Schema(description = "Technical/business cart id", example = "cart-1")
        private String cartId;
        @Schema(description = "Payment amount", example = "27.0")
        private Double amount;
        @Schema(description = "Payment provider", example = "DUMMY")
        private String provider;

        // Optional fields if provided by client
        @Schema(description = "Optional paymentId", example = "pay-1")
        private String paymentId;
        @Schema(description = "Optional status", example = "INITIATED")
        private String status;
        @Schema(description = "Optional createdAt (ISO8601)", example = "2025-08-27T12:00:00Z")
        private String createdAt;
        @Schema(description = "Optional updatedAt (ISO8601)", example = "2025-08-27T12:00:00Z")
        private String updatedAt;
    }

    @Data
    @Schema(name = "UpdatePaymentRequest", description = "Request to update a Payment (partial allowed)")
    public static class UpdatePaymentRequest {
        @Schema(description = "Technical/business cart id", example = "cart-1")
        private String cartId;
        @Schema(description = "Payment amount", example = "27.0")
        private Double amount;
        @Schema(description = "Payment provider", example = "DUMMY")
        private String provider;

        @Schema(description = "Optional paymentId", example = "pay-1")
        private String paymentId;
        @Schema(description = "Optional status", example = "PAID")
        private String status;
        @Schema(description = "Optional createdAt (ISO8601)", example = "2025-08-27T12:00:00Z")
        private String createdAt;
        @Schema(description = "Optional updatedAt (ISO8601)", example = "2025-08-27T12:00:00Z")
        private String updatedAt;
    }

    @Data
    @Schema(name = "PaymentResponse", description = "Payment entity representation")
    public static class PaymentResponse {
        @Schema(description = "Payment business id", example = "payment-1")
        private String paymentId;
        @Schema(description = "Cart id", example = "cart-1")
        private String cartId;
        @Schema(description = "Amount", example = "27.0")
        private Double amount;
        @Schema(description = "Status", example = "PAID")
        private String status;
        @Schema(description = "Provider", example = "DUMMY")
        private String provider;
        @Schema(description = "Created at (ISO8601)", example = "2025-08-27T12:00:00Z")
        private String createdAt;
        @Schema(description = "Updated at (ISO8601)", example = "2025-08-27T12:00:00Z")
        private String updatedAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing single technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID (UUID)", example = "6f1e2f6a-1234-4e5b-8cde-123456789abc")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical IDs", example = "[\"6f1e2f6a-1234-4e5b-8cde-123456789abc\"]")
        private List<String> technicalIds;

        public TechnicalIdsResponse() {}

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }
}