package com.java_template.application.controller.order.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/ui/order")
@Tag(name = "Order UI API", description = "APIs to create and retrieve Order entities")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Order", description = "Create an Order event (returns technicalId). Payload contains paymentId and cartId as specified by functional requirements.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/create")
    public ResponseEntity<TechnicalIdResponse> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Create order request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateOrderRequest.class)))
            @RequestBody CreateOrderRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    com.java_template.application.entity.order.version_1.Order.ENTITY_NAME,
                    com.java_template.application.entity.order.version_1.Order.ENTITY_VERSION,
                    request
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            // assign directly to field to avoid relying on generated Lombok setter in environments where annotation processing might be disabled
            resp.technicalId = entityId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create order: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException creating order", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error creating order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Order", description = "Retrieve an Order entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            OrderResponse orderResponse = objectMapper.treeToValue(dataPayload.getData(), OrderResponse.class);
            return ResponseEntity.ok(orderResponse);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid getOrder request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException retrieving order", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static DTO classes for requests/responses

    @Data
    @NoArgsConstructor
    @Schema(name = "CreateOrderRequest", description = "Request payload to create an Order event")
    public static class CreateOrderRequest {
        @Schema(description = "Payment technical id", example = "t-payment-456")
        private String paymentId;

        @Schema(description = "Cart technical id", example = "t-cart-123")
        private String cartId;
    }

    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier of the created entity", example = "t-order-789")
        public String technicalId;
    }

    @Data
    @NoArgsConstructor
    @Schema(name = "OrderResponse", description = "Order entity representation")
    public static class OrderResponse {
        @Schema(description = "Order business id")
        private String orderId;

        @Schema(description = "Order number (short ULID)")
        private String orderNumber;

        @Schema(description = "Order status")
        private String status;

        @Schema(description = "Created timestamp")
        private String createdAt;

        @Schema(description = "Updated timestamp")
        private String updatedAt;

        @Schema(description = "Guest contact information")
        private GuestContact guestContact;

        @Schema(description = "Order lines")
        private java.util.List<Line> lines;

        @Schema(description = "Order totals")
        private Totals totals;

        @Data
        @NoArgsConstructor
        @Schema(name = "GuestContact", description = "Guest contact information")
        public static class GuestContact {
            @Schema(description = "Address")
            private Address address;

            @Schema(description = "Email")
            private String email;

            @Schema(description = "Name")
            private String name;

            @Schema(description = "Phone")
            private String phone;
        }

        @Data
        @NoArgsConstructor
        @Schema(name = "Address", description = "Postal address")
        public static class Address {
            @Schema(description = "City")
            private String city;

            @Schema(description = "Country")
            private String country;

            @Schema(description = "Line 1")
            private String line1;

            @Schema(description = "Postcode")
            private String postcode;
        }

        @Data
        @NoArgsConstructor
        @Schema(name = "Line", description = "Order line")
        public static class Line {
            @Schema(description = "Product SKU")
            private String sku;

            @Schema(description = "Product name")
            private String name;

            @Schema(description = "Quantity")
            private Integer qty;

            @Schema(description = "Unit price")
            private Double unitPrice;

            @Schema(description = "Line total")
            private Double lineTotal;
        }

        @Data
        @NoArgsConstructor
        @Schema(name = "Totals", description = "Order totals")
        public static class Totals {
            @Schema(description = "Grand total")
            private Double grand;

            @Schema(description = "Items total")
            private Double items;
        }
    }
}