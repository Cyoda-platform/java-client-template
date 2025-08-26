package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/orders/v1")
@Tag(name = "Order Controller", description = "Proxy controller for Order entity operations (v1)")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Order (checkout event)",
            description = "Accepts a checkout request (cartId + optional paymentReference) and publishes it as an Order model event.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/checkout")
    public ResponseEntity<TechnicalIdResponse> checkout(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Checkout payload", required = true,
                    content = @Content(schema = @Schema(implementation = CheckoutRequest.class)))
            @RequestBody CheckoutRequest request) {
        try {
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    request
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for checkout: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during checkout", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during checkout", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during checkout", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Add Order",
            description = "Add a full Order entity. Controller acts as a proxy to EntityService; no business logic is performed here.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> addOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order entity payload", required = true,
                    content = @Content(schema = @Schema(implementation = Order.class)))
            @RequestBody Order order) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    order
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid addOrder request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during addOrder", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during addOrder", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during addOrder", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Add multiple Orders (batch)",
            description = "Add multiple Order entities in batch.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<List<String>> addOrders(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Order entities", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Order.class))))
            @RequestBody List<Order> orders) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    orders
            );
            List<UUID> uuids = idsFuture.get();
            List<String> ids = uuids.stream().map(UUID::toString).toList();
            return ResponseEntity.ok(ids);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid addOrders request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during addOrders", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during addOrders", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during addOrders", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Order by technicalId",
            description = "Retrieve a full Order entity by technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getOrderById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode result = itemFuture.get();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getOrderById request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during getOrderById", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getOrderById", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during getOrderById", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get all Orders",
            description = "Retrieve all Order entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<ArrayNode> getAllOrders() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION)
            );
            ArrayNode result = itemsFuture.get();
            return ResponseEntity.ok(result);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during getAllOrders", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getAllOrders", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during getAllOrders", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Search Orders by condition",
            description = "Search Orders by given search condition. This performs an in-memory filter.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<ArrayNode> searchOrders(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode result = filteredFuture.get();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchOrders request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during searchOrders", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during searchOrders", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during searchOrders", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Order",
            description = "Update an existing Order by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> updateOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order entity payload", required = true,
                    content = @Content(schema = @Schema(implementation = Order.class)))
            @RequestBody Order order) {
        try {
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    order
            );
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateOrder request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during updateOrder", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateOrder", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during updateOrder", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Order",
            description = "Delete an existing Order by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deleteOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteOrder request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during deleteOrder", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteOrder", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error during deleteOrder", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    @Schema(name = "CheckoutRequest", description = "Request to trigger an order creation from a shopping cart")
    public static class CheckoutRequest {
        @Schema(description = "Business cart id", example = "c123")
        private String cartId;
        @Schema(description = "Optional external payment reference", example = "external-ref-optional")
        private String paymentReference;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical UUID of the created/affected entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}