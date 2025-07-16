package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/api")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;

    private static final List<String> AVAILABLE_SIZES = List.of("small", "medium", "large");
    private static final List<String> AVAILABLE_TOPPINGS = List.of("pepperoni", "mushrooms", "onions", "sausage", "bacon");
    private static final List<String> PAYMENT_METHODS = List.of("credit_card", "paypal", "cash");
    private static final String ENTITY_NAME = "order";

    @PostMapping("/orders")
    public CompletableFuture<ResponseEntity<CreateOrderResponse>> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        logger.info("Received create order request for customerId={}", request.getCustomerId());

        // Basic validations remain here
        if (!AVAILABLE_SIZES.contains(request.getPizzaSize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pizza size");
        }
        if (request.getToppings().isEmpty() || !AVAILABLE_TOPPINGS.containsAll(request.getToppings())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid toppings");
        }
        if (!PAYMENT_METHODS.contains(request.getPaymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
        }

        ObjectNode entity = entityService.getObjectMapper().createObjectNode();
        entity.put("customerId", request.getCustomerId());
        entity.put("pizzaSize", request.getPizzaSize());
        ArrayNode toppingsArray = entity.putArray("toppings");
        request.getToppings().forEach(toppingsArray::add);
        entity.put("deliveryAddress", request.getDeliveryAddress());
        entity.put("scheduledTime", request.getScheduledTime());
        entity.put("paymentMethod", request.getPaymentMethod());
        entity.put("paymentDetails", request.getPaymentDetails());

        // Add the workflow function processOrder, which handles all async logic and state changes before persistence
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entity, this::processOrder)
                .thenApply(id -> {
                    CreateOrderResponse resp = new CreateOrderResponse();
                    resp.setOrderId(id.toString());
                    // Read back fields set by workflow function
                    resp.setStatus(entity.path("status").asText("created").toLowerCase());
                    resp.setEstimatedDeliveryTime(entity.path("estimatedDeliveryTime").asText(null));
                    return ResponseEntity.status(HttpStatus.CREATED).body(resp);
                });
    }

    /**
     * Workflow function to process the order entity asynchronously before persistence.
     * Modifies entity state directly.
     * @param entity the JSON ObjectNode representing the order entity
     * @return CompletableFuture<ObjectNode> with processed entity
     */
    private CompletableFuture<ObjectNode> processOrder(ObjectNode entity) {
        logger.info("Processing order entity in workflow function processOrder for customerId={}", entity.path("customerId").asText());

        // Validate payment asynchronously
        return validatePaymentAsync(entity.path("paymentMethod").asText(), entity.path("paymentDetails").asText())
                .thenApply(paymentOk -> {
                    if (!paymentOk) {
                        throw new RuntimeException("Payment validation failed");
                    }
                    return entity;
                })
                .thenApply(enrichedEntity -> {
                    // Set default status if missing or empty
                    String status = enrichedEntity.path("status").asText(null);
                    if (status == null || status.isEmpty()) {
                        enrichedEntity.put("status", "CREATED");
                    }
                    return enrichedEntity;
                })
                .thenApply(enrichedEntity -> {
                    // Set estimated delivery time if missing
                    if (enrichedEntity.path("estimatedDeliveryTime").isMissingNode()) {
                        Instant eta = Instant.now().plusSeconds(30 * 60);
                        enrichedEntity.put("estimatedDeliveryTime", eta.toString());
                    }
                    return enrichedEntity;
                })
                .thenApply(enrichedEntity -> {
                    // Fire-and-forget notification async task
                    CompletableFuture.runAsync(() -> sendOrderNotification(enrichedEntity)).exceptionally(ex -> {
                        logger.error("Failed to send order notification", ex);
                        return null;
                    });
                    return enrichedEntity;
                });
    }

    /**
     * Simulate asynchronous payment validation.
     * Replace with real async payment gateway call.
     */
    private CompletableFuture<Boolean> validatePaymentAsync(String method, String details) {
        logger.info("Validating payment asynchronously method={}", method);
        // Simulate async payment validation with immediate positive result
        return CompletableFuture.supplyAsync(() -> true);
    }

    /**
     * Simulate fire-and-forget notification sending.
     */
    private void sendOrderNotification(ObjectNode orderEntity) {
        logger.info("Sending notification for order customerId={}", orderEntity.path("customerId").asText());
        // Simulate notification logic here
        try {
            Thread.sleep(100); // simulate some delay
        } catch (InterruptedException ignored) {
        }
    }

    @GetMapping("/orders/{orderId}")
    public CompletableFuture<ResponseEntity<OrderStatusResponse>> getOrderStatus(@PathVariable @NotBlank String orderId) {
        logger.info("Retrieving status for orderId={}", orderId);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
                    }
                    OrderStatusResponse resp = new OrderStatusResponse();
                    resp.setOrderId(orderId);
                    resp.setStatus(item.path("status").asText("unknown").toLowerCase());
                    resp.setPizzaSize(item.path("pizzaSize").asText(null));
                    // toppings is an array, convert accordingly or fallback to empty
                    JsonNode toppingsNode = item.path("toppings");
                    if (toppingsNode.isArray()) {
                        resp.setToppings(toppingsNode.findValuesAsText(null));
                    } else {
                        resp.setToppings(List.of());
                    }
                    resp.setDeliveryAddress(item.path("deliveryAddress").asText(null));
                    resp.setEstimatedDeliveryTime(item.path("estimatedDeliveryTime").asText(null));
                    return ResponseEntity.ok(resp);
                });
    }

    @GetMapping("/options")
    public ResponseEntity<AvailableOptionsResponse> getAvailableOptions() {
        logger.info("Listing available options");
        AvailableOptionsResponse resp = new AvailableOptionsResponse();
        resp.setSizes(AVAILABLE_SIZES);
        resp.setToppings(AVAILABLE_TOPPINGS);
        resp.setPaymentMethods(PAYMENT_METHODS);
        return ResponseEntity.ok(resp);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        ErrorResponse err = new ErrorResponse();
        err.setError(ex.getStatusCode().toString());
        err.setMessage(ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @Data
    public static class CreateOrderRequest {
        @NotBlank
        private String customerId;
        @NotBlank
        private String pizzaSize;
        @Size(min = 1)
        private List<@NotBlank String> toppings;
        @NotBlank
        private String deliveryAddress;
        @Pattern(regexp = "^\\d{4}-[01]\\d-[0-3]\\d[T ](?:[0-2]\\d:[0-5]\\d:[0-5]\\d)Z?$",
                 message = "scheduledTime must be ISO8601")
        private String scheduledTime;
        @NotBlank
        private String paymentMethod;
        @NotBlank
        private String paymentDetails;
    }

    @Data
    public static class CreateOrderResponse {
        private String orderId;
        private String status;
        private String estimatedDeliveryTime;
    }

    @Data
    public static class OrderStatusResponse {
        private String orderId;
        private String status;
        private String pizzaSize;
        private List<String> toppings;
        private String deliveryAddress;
        private String estimatedDeliveryTime;
    }

    @Data
    public static class AvailableOptionsResponse {
        private List<String> sizes;
        private List<String> toppings;
        private List<String> paymentMethods;
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}