package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

/**
 * Controller is a thin proxy to EntityService for Order entity.
 * All business logic must reside in workflows; controller only forwards requests.
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "Order", description = "Order entity operations (v1)")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Order", description = "Create an Order entity (triggers ORDER workflow). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Order creation payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateOrderRequest.class),
                            examples = @ExampleObject(value = "{\"petId\":\"pet-123\",\"buyerName\":\"Alex\",\"buyerContact\":\"alex@example.com\",\"type\":\"adoption\",\"notes\":\"I love cats!\"}"))
            )
            @RequestBody CreateOrderRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getPetId() == null || request.getPetId().isBlank()) {
                throw new IllegalArgumentException("petId is required");
            }
            if (request.getBuyerName() == null || request.getBuyerName().isBlank()) {
                throw new IllegalArgumentException("buyerName is required");
            }
            if (request.getBuyerContact() == null || request.getBuyerContact().isBlank()) {
                throw new IllegalArgumentException("buyerContact is required");
            }
            if (request.getType() == null || request.getType().isBlank()) {
                throw new IllegalArgumentException("type is required");
            }

            // Build Order entity - minimal setup; workflows handle business transitions.
            Order order = new Order();
            // generate technical orderId
            String generatedOrderId = UUID.randomUUID().toString();
            order.setOrderId(generatedOrderId);
            order.setPetId(request.getPetId());
            order.setBuyerName(request.getBuyerName());
            order.setBuyerContact(request.getBuyerContact());
            order.setType(request.getType());
            order.setNotes(request.getNotes());
            // set minimal required fields for entity validity; detailed business handled in workflows
            order.setStatus("PLACED");
            order.setPlacedAt(Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    Order.ENTITY_VERSION,
                    order
            );
            UUID entityId = idFuture.get();

            CreateOrderResponse resp = new CreateOrderResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create order: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating order", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Order by technicalId", description = "Retrieve stored Order by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
            }

            OrderResponse response = objectMapper.treeToValue(node, OrderResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get order {}: {}", technicalId, iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving order {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving order {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateOrderRequest", description = "Request payload to create an Order")
    public static class CreateOrderRequest {
        @Schema(description = "Referenced Pet business id", example = "pet-123", required = true)
        private String petId;

        @Schema(description = "Buyer name", example = "Alex", required = true)
        private String buyerName;

        @Schema(description = "Buyer contact (email/phone)", example = "alex@example.com", required = true)
        private String buyerContact;

        @Schema(description = "Order type (adoption/purchase)", example = "adoption", required = true)
        private String type;

        @Schema(description = "Optional notes", example = "I love cats!")
        private String notes;
    }

    @Data
    @Schema(name = "CreateOrderResponse", description = "Response returned after creating an Order")
    public static class CreateOrderResponse {
        @Schema(description = "Technical id of the created Order", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;
    }

    @Data
    @Schema(name = "OrderResponse", description = "Order entity representation returned by GET")
    public static class OrderResponse {
        @Schema(description = "Business order id", example = "order-789")
        private String orderId;

        @Schema(description = "Referenced pet id", example = "pet-123")
        private String petId;

        @Schema(description = "Buyer name", example = "Alex")
        private String buyerName;

        @Schema(description = "Buyer contact", example = "alex@example.com")
        private String buyerContact;

        @Schema(description = "Order type", example = "adoption")
        private String type;

        @Schema(description = "Order status", example = "PLACED")
        private String status;

        @Schema(description = "ISO timestamp when order was placed", example = "2025-08-28T12:05:00Z")
        private String placedAt;

        @Schema(description = "Optional notes", example = "I love cats!")
        private String notes;
    }
}