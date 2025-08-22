package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Tag(name = "Order API", description = "Operations for Order entity")
@RestController
@RequestMapping("/api/v1/order")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Order", description = "Create a single Order entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateOrderRequest.class)))
            @Valid @RequestBody CreateOrderRequest request) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    request.getEntity()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument when creating Order", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while creating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Orders", description = "Create multiple Order entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdsResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createOrders(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Orders to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateOrdersRequest.class)))
            @Valid @RequestBody CreateOrdersRequest request) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    request.getEntities()
            );
            List<UUID> ids = idsFuture.get();
            IdsResponse resp = new IdsResponse(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument when creating Orders", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while creating Orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating Orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Order by technicalId", description = "Retrieve a single Order by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId when retrieving Order: {}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Orders", description = "Retrieve all Orders")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getOrders() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving Orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving Orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Orders", description = "Retrieve Orders matching a simple search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchOrders(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = FilterRequest.class)))
            @Valid @RequestBody FilterRequest request) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    request.getCondition(),
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while searching Orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when searching Orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Order", description = "Update an existing Order by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateOrderRequest.class)))
            @Valid @RequestBody UpdateOrderRequest request) {
        try {
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    request.getEntity()
            );
            UUID id = updatedId.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument when updating Order: {}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when updating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Order", description = "Delete an Order by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId when deleting Order: {}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when deleting Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in execution", cause);
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution exception", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateOrderRequest", description = "Request to create a single Order")
    public static class CreateOrderRequest {
        @Schema(description = "Order entity payload as JSON", required = true, implementation = ObjectNode.class)
        private ObjectNode entity;
    }

    @Data
    @Schema(name = "CreateOrdersRequest", description = "Request to create multiple Orders")
    public static class CreateOrdersRequest {
        @Schema(description = "List of Order entity payloads as JSON", required = true)
        private List<ObjectNode> entities;
    }

    @Data
    @Schema(name = "UpdateOrderRequest", description = "Request to update an Order")
    public static class UpdateOrderRequest {
        @Schema(description = "Order entity payload as JSON", required = true, implementation = ObjectNode.class)
        private ObjectNode entity;
    }

    @Data
    @Schema(name = "FilterRequest", description = "Request containing a search condition for Orders")
    public static class FilterRequest {
        @Schema(description = "Search condition", required = true, implementation = SearchConditionRequest.class)
        private SearchConditionRequest condition;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response returning single id")
    public static class IdResponse {
        @Schema(description = "Technical id", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
        private String id;

        public IdResponse(String id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response returning multiple ids")
    public static class IdsResponse {
        @Schema(description = "List of technical ids", required = true)
        private List<String> ids;

        public IdsResponse(List<String> ids) {
            this.ids = ids;
        }
    }
}