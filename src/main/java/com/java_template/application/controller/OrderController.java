package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/entity/Order")
@Tag(name = "Order")
public class OrderController {
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Order (place order)", description = "Place an order from a cart. requestId required for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "409", description = "Conflict")
    })
    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            UUID id = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid place order request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Error placing order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Order", description = "Retrieve Order by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOrder(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(node);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Orders", description = "List all Orders")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Order.class))))
    })
    @GetMapping
    public ResponseEntity<?> listOrders() {
        try {
            ArrayNode arr = entityService.getItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION)
            ).get();
            return ResponseEntity.ok(arr);
        } catch (Exception e) {
            logger.error("Error listing orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Start Picking", description = "Start picking for an order. requestId required for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class)))
    })
    @PostMapping("/{technicalId}/start-picking")
    public ResponseEntity<?> startPicking(@Parameter(name = "technicalId", description = "Technical ID of the order") @PathVariable String technicalId,
                                          @RequestBody IdempotentRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            UUID id = entityService.updateItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error starting picking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Mark Sent", description = "Mark order as sent. requestId required for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class)))
    })
    @PostMapping("/{technicalId}/mark-sent")
    public ResponseEntity<?> markSent(@Parameter(name = "technicalId", description = "Technical ID of the order") @PathVariable String technicalId,
                                      @RequestBody IdempotentRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            UUID id = entityService.updateItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error marking order sent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Query Orders", description = "Query Orders by condition (in-memory)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Order.class))))
    })
    @PostMapping("/query")
    public ResponseEntity<?> queryOrders(@RequestBody SearchConditionRequest condition) {
        try {
            ArrayNode arr = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    condition,
                    true
            ).get();
            return ResponseEntity.ok(arr);
        } catch (Exception e) {
            logger.error("Error querying orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class PlaceOrderRequest {
        @Schema(description = "Idempotency request id", required = true)
        private String requestId;
        @Schema(description = "Cart technical id")
        private String cartTechnicalId;
        @Schema(description = "User technical id")
        private String userTechnicalId;
        @Schema(description = "Address technical id")
        private String addressTechnicalId;
    }

    @Data
    static class IdempotentRequest {
        @Schema(description = "Idempotency request id", required = true)
        private String requestId;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created resource")
        private String technicalId;
    }
}
