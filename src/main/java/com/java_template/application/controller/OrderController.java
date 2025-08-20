package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.OrderItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Order API", description = "APIs for Order entity (event-driven)")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Order", description = "Persist an Order and start its workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createOrder(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order payload") @RequestBody OrderRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            // Map DTO -> Entity
            Order order = new Order();
            order.setOrderId(request.getOrderId());
            order.setCustomerId(request.getCustomerId());
            order.setCurrency(request.getCurrency());
            order.setShippingAddress(request.getShippingAddress());
            order.setTotalAmount(request.getTotalAmount());
            order.setCreatedAt(request.getCreatedAt());
            order.setStatus(request.getStatus());

            if (request.getItems() != null) {
                List<OrderItem> items = new ArrayList<>();
                for (OrderRequest.OrderItemDto it : request.getItems()) {
                    OrderItem oi = new OrderItem();
                    oi.setSku(it.getSku());
                    oi.setQuantity(it.getQuantity());
                    oi.setPrice(it.getPrice());
                    items.add(oi);
                }
                order.setItems(items);
            }

            if (!order.isValid()) {
                throw new IllegalArgumentException("Invalid order payload");
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    order
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request to create order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating order", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Order by technicalId", description = "Retrieve the persisted Order by internal technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOrder(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request to get order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving order", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs
    @Data
    @Schema(name = "OrderRequest", description = "Order request payload")
    public static class OrderRequest {
        private String orderId;
        private String customerId;
        private List<OrderItemDto> items;
        private Double totalAmount;
        private String currency;
        private String shippingAddress;
        private String status;
        private String createdAt;

        @Data
        @Schema(name = "OrderItem")
        public static class OrderItemDto {
            private String sku;
            private Integer quantity;
            private Double price;
        }
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
