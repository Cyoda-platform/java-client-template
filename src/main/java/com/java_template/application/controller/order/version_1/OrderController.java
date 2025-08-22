package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.java_template.application.entity.order.version_1.Order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order API", description = "CRUD and query endpoints for Order entity (version 1). Controller acts as a proxy to EntityService; business logic lives in workflows.")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Order", description = "Persist a new Order entity. Returns only technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOrder(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order payload") @RequestBody OrderRequest request) {
        try {
            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for createOrder", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error during createOrder", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during createOrder", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Orders", description = "Persist multiple Order entities. Returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createOrdersBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of orders") @RequestBody BulkOrderRequest request) {
        try {
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
            logger.error("Invalid request for createOrdersBulk", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error during createOrdersBulk", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during createOrdersBulk", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Order by technicalId", description = "Retrieve an Order by its technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderGetResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOrderById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode entity = itemFuture.get();
            OrderGetResponse resp = new OrderGetResponse(technicalId, entity);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId for getOrderById", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error during getOrderById", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during getOrderById", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Orders", description = "Retrieve all Order entities.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllOrders() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error during getAllOrders", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during getAllOrders", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Orders by condition", description = "Retrieve Order entities matching provided search condition (basic field-based).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchOrders(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request") @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid search condition for searchOrders", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error during searchOrders", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during searchOrders", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Order", description = "Update an existing Order entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order payload") @RequestBody OrderRequest request
    ) {
        try {
            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            );
            UUID id = updatedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for updateOrder", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error during updateOrder", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during updateOrder", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Order", description = "Delete an Order entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId for deleteOrder", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error during deleteOrder", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during deleteOrder", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    // DTOs

    @Data
    @Schema(name = "OrderRequest", description = "Order request payload (matches Order entity fields).")
    public static class OrderRequest {
        @Schema(description = "Business id of the order")
        private String id;

        @Schema(description = "Originating cart id")
        private String cartId;

        @Schema(description = "User id")
        private String userId;

        @Schema(description = "Order status")
        private String status;

        @Schema(description = "Snapshot of items", implementation = ArrayNode.class)
        private ArrayNode itemsSnapshot;

        @Schema(description = "Total amount")
        private BigDecimal totalAmount;

        @Schema(description = "Shipping address id")
        private String shippingAddressId;

        @Schema(description = "Billing address id")
        private String billingAddressId;

        @Schema(description = "Created at timestamp", type = "string", format = "date-time")
        private OffsetDateTime createdAt;

        @Schema(description = "Updated at timestamp", type = "string", format = "date-time")
        private OffsetDateTime updatedAt;
    }

    @Data
    @Schema(name = "BulkOrderRequest", description = "Bulk orders request")
    public static class BulkOrderRequest {
        @Schema(description = "List of orders", implementation = ArrayNode.class)
        private List<OrderRequest> orders;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a technical id.")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technical ids.")
    public static class TechnicalIdsResponse {
        @Schema(description = "Technical ids of persisted entities")
        private List<String> technicalIds = new java.util.ArrayList<>();
    }

    @Data
    @Schema(name = "OrderGetResponse", description = "GET response for an Order entity.")
    public static class OrderGetResponse {
        @Schema(description = "Technical id of the entity")
        private String technicalId;

        @Schema(description = "Returned entity", implementation = ObjectNode.class)
        private ObjectNode entity;

        public OrderGetResponse() {}

        public OrderGetResponse(String technicalId, ObjectNode entity) {
            this.technicalId = technicalId;
            this.entity = entity;
        }
    }
}