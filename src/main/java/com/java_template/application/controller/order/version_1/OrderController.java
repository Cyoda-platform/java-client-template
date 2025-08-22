package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.order.version_1.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * OrderController - proxy controller delegating to EntityService for Order entity operations.
 *
 * Rules followed:
 * - No business logic implemented in controller.
 * - Entity classes reused from package com.java_template.application.entity.order.version_1.
 * - Request/Response DTOs defined as static classes below with Swagger Schema annotations.
 */
@RestController
@RequestMapping(path = "/api/order/v1", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
@Tag(name = "Order", description = "Operations for Order entity (version 1)")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Order", description = "Create a single Order entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addOrder(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Order payload", content = @Content(schema = @Schema(implementation = OrderRequest.class)))
            @RequestBody OrderRequest request) {
        try {
            if (request == null || request.getOrder() == null) {
                throw new IllegalArgumentException("Order payload is required");
            }

            ObjectNode data = objectMapper.valueToTree(request.getOrder());
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    data
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid addOrder request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating order", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while creating order", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Create Orders (bulk)", description = "Create multiple Order entities in bulk")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addOrders(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Bulk orders payload", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BulkOrderRequest.class))))
            @RequestBody BulkOrderRequest request) {
        try {
            if (request == null || request.getOrders() == null || request.getOrders().isEmpty()) {
                throw new IllegalArgumentException("Orders payload is required");
            }

            ArrayNode data = objectMapper.valueToTree(request.getOrders());
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    data
            );

            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            for (UUID id : ids) resp.getTechnicalIds().add(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid addOrders request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating orders", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while creating orders", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Get Order", description = "Retrieve a single Order by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getOrder request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving order", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving order", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Get all Orders", description = "Retrieve all Order entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOrders() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving orders", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving orders", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Search Orders", description = "Retrieve orders by a simple search condition (in-memory)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchOrders(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchOrders request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching orders", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while searching orders", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Update Order", description = "Update an existing Order by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order payload", content = @Content(schema = @Schema(implementation = OrderRequest.class)))
            @RequestBody OrderRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null || request.getOrder() == null) throw new IllegalArgumentException("Order payload is required");

            ObjectNode data = objectMapper.valueToTree(request.getOrder());
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            );

            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateOrder request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating order", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while updating order", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Delete Order", description = "Delete an Order by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<?> deleteOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteOrder request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting order", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting order", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    private ResponseEntity<?> handleExecutionExceptionCause(Throwable cause) {
        if (cause == null) {
            logger.error("ExecutionException with null cause");
            return ResponseEntity.status(500).body("Internal error");
        }
        if (cause instanceof NoSuchElementException) {
            logger.warn("Resource not found: {}", cause.getMessage());
            return ResponseEntity.status(404).body(cause.getMessage());
        }
        if (cause instanceof IllegalArgumentException) {
            logger.warn("Bad request cause: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        }
        logger.error("ExecutionException cause", cause);
        return ResponseEntity.status(500).body("Internal error: " + cause.getMessage());
    }

    /*
     * Static DTO classes for requests and responses
     */

    @Data
    public static class OrderRequest {
        @Schema(description = "Order entity payload", required = true, implementation = Order.class)
        private Order order;
    }

    @Data
    public static class BulkOrderRequest {
        @Schema(description = "List of order entities", required = true, implementation = Order.class)
        private List<Order> orders;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the processed entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    public static class TechnicalIdsResponse {
        @Schema(description = "Technical ids of the processed entities")
        private List<String> technicalIds = new java.util.ArrayList<>();
    }

    @Data
    public static class OrderResponse {
        @Schema(description = "Order JSON payload", implementation = ObjectNode.class)
        private ObjectNode order;
    }
}