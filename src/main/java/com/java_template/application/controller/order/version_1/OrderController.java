package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Order entity.
 * All business logic must be implemented in workflows/processors, not here.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order Controller", description = "Proxy endpoints for Order entity")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Order", description = "Create an Order entity (triggers workflows). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order create request", required = true,
                    content = @Content(schema = @Schema(implementation = OrderCreateRequest.class)))
            @RequestBody OrderCreateRequest request) {
        try {
            // Basic validation
            if (request == null) {
                return ResponseEntity.badRequest().build();
            }
            if (request.getId() == null || request.getId().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Convert request DTO to entity
            Order orderEntity = objectMapper.convertValue(request, Order.class);

            UUID technicalId = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    orderEntity
            ).get();

            OrderCreateResponse response = new OrderCreateResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while creating Order", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument while creating Order", iae);
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Order", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Order by technicalId", description = "Retrieve Order entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderGetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<OrderGetResponse> getOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            UUID techId = UUID.fromString(technicalId);

            ObjectNode node = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    techId
            ).get();

            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Convert stored JSON to Order entity for response payload
            Order orderPayload = objectMapper.convertValue(node, Order.class);

            OrderGetResponse response = new OrderGetResponse();
            response.setTechnicalId(techId.toString());
            response.setOrder(orderPayload);

            return ResponseEntity.ok(response);

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while fetching Order", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId provided for Order fetch", iae);
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching Order", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while fetching Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Request/Response DTOs

    @Data
    public static class OrderCreateRequest {
        @Schema(description = "Business id of the order", example = "O-555")
        private String id;

        @Schema(description = "User id (nullable for guest)", example = "U-1")
        private String userId;

        @Schema(description = "Order items snapshot")
        private Object[] items;

        @Schema(description = "Total amount", example = "0.0")
        private Double total;

        @Schema(description = "Currency code", example = "USD")
        private String currency;

        @Schema(description = "Shipping address business id", example = "A-1")
        private String shippingAddressId;

        @Schema(description = "Billing address business id", example = "A-1")
        private String billingAddressId;

        @Schema(description = "Payment status (optional at creation)", example = "Pending")
        private String paymentStatus;

        @Schema(description = "Order status (optional at creation)", example = "Created")
        private String status;

        @Schema(description = "Created timestamp (optional)", example = "2023-01-01T00:00:00Z")
        private String createdAt;
    }

    @Data
    public static class OrderCreateResponse {
        @Schema(description = "Technical id assigned to the entity", example = "tech-order-ord001")
        private String technicalId;
    }

    @Data
    public static class OrderGetResponse {
        @Schema(description = "Technical id of the entity", example = "tech-order-ord001")
        private String technicalId;

        @Schema(description = "Order entity payload")
        private Order order;
    }
}