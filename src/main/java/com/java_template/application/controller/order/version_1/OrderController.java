package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order/v1")
@Tag(name = "Order API", description = "Operations for orders (version 1)")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Order", description = "Create a single order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @Valid @RequestBody OrderCreateRequest request
    ) {
        try {
            if (request.getId() == null || request.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("id is required");
            }

            ObjectNode data = (ObjectNode) objectMapper.valueToTree(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    data
            );

            UUID technicalId = idFuture.get();
            OrderCreateResponse resp = new OrderCreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create order: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating order", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException | TimeoutException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating order", ex);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while creating order", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Create Orders (batch)", description = "Create multiple orders in batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders created"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/batch")
    public ResponseEntity<BatchTechnicalIdResponse> createOrders(
            @Valid @RequestBody List<OrderCreateRequest> requests
    ) {
        try {
            if (requests == null) {
                throw new IllegalArgumentException("Request list cannot be null");
            }

            ArrayNode arr = objectMapper.createArrayNode();
            for (OrderCreateRequest r : requests) {
                arr.add(objectMapper.valueToTree(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    arr
            );

            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            BatchTechnicalIdResponse resp = new BatchTechnicalIdResponse();
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid batch create request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while batch creating orders", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException | TimeoutException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while batch creating orders", ex);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while batch creating orders", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Order by technicalId", description = "Retrieve an order by its technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<OrderGetResponse> getOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            UUID techId = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    techId
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(404).build();
            }
            OrderGetResponse resp = new OrderGetResponse();
            resp.setTechnicalId(techId.toString());
            resp.setOrder(node);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId '{}': {}", technicalId, ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while getting order", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException | TimeoutException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting order", ex);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while getting order", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get all Orders", description = "Retrieve all orders")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<ArrayNode> getOrders() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);

        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while getting orders", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException | TimeoutException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting orders", ex);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while getting orders", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Search Orders by condition", description = "Search orders using a SearchConditionRequest")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/search")
    public ResponseEntity<ArrayNode> searchOrders(
            @Valid @RequestBody SearchConditionRequest condition
    ) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while searching orders", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException | TimeoutException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching orders", ex);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while searching orders", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Order", description = "Update an existing order by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order updated"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> updateOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @Valid @RequestBody OrderUpdateRequest request
    ) {
        try {
            UUID techId = UUID.fromString(technicalId);
            ObjectNode data = (ObjectNode) objectMapper.valueToTree(request);

            CompletableFuture<UUID> updated = entityService.updateItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    techId,
                    data
            );

            UUID updatedId = updated.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid update request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while updating order", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException | TimeoutException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating order", ex);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while updating order", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Order", description = "Delete an order by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order deleted"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deleteOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            UUID techId = UUID.fromString(technicalId);

            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    techId
            );

            UUID deletedId = deleted.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId '{}': {}", technicalId, ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while deleting order", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException | TimeoutException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting order", ex);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting order", ex);
            return ResponseEntity.status(500).build();
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "OrderCreateRequest", description = "Request to create an order")
    public static class OrderCreateRequest {
        @Schema(description = "Business id of the order", example = "ORD-123")
        private String id;

        @Schema(description = "User id", example = "user-uuid-or-id")
        private String userId;

        @Schema(description = "Total amount")
        private Double total;

        @Schema(description = "Currency code", example = "USD")
        private String currency;

        @Schema(description = "Arbitrary order payload (items, addresses etc.)")
        private Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "OrderUpdateRequest", description = "Request to update an order")
    public static class OrderUpdateRequest {
        @Schema(description = "Business id of the order", example = "ORD-123")
        private String id;

        @Schema(description = "User id", example = "user-uuid-or-id")
        private String userId;

        @Schema(description = "Total amount")
        private Double total;

        @Schema(description = "Currency code", example = "USD")
        private String currency;

        @Schema(description = "Arbitrary order payload (items, addresses etc.)")
        private Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "OrderCreateResponse", description = "Response when an order is created")
    public static class OrderCreateResponse {
        @Schema(description = "Technical id (UUID) of the created entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchTechnicalIdResponse", description = "Response when multiple entities are created")
    public static class BatchTechnicalIdResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)")
        private String technicalId;
    }

    @Data
    @Schema(name = "OrderGetResponse", description = "Response for getting an order")
    public static class OrderGetResponse {
        @Schema(description = "Technical id (UUID) of the order")
        private String technicalId;

        @Schema(description = "Order payload as JSON")
        private ObjectNode order;
    }
}