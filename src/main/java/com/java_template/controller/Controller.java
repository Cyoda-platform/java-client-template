package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final List<String> AVAILABLE_SIZES = List.of("small", "medium", "large");
    private static final List<String> AVAILABLE_TOPPINGS = List.of("pepperoni", "mushrooms", "onions", "sausage", "bacon");
    private static final List<String> PAYMENT_METHODS = List.of("credit_card", "paypal", "cash");
    private static final String ENTITY_NAME = "order";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/orders")
    public CompletableFuture<ResponseEntity<CreateOrderResponse>> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        logger.info("Received create order request for customerId={}", request.getCustomerId());

        if (!AVAILABLE_SIZES.contains(request.getPizzaSize())) {
            logger.error("Invalid pizza size: {}", request.getPizzaSize());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pizza size");
        }
        if (request.getToppings().isEmpty() || !AVAILABLE_TOPPINGS.containsAll(request.getToppings())) {
            logger.error("Invalid toppings: {}", request.getToppings());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid toppings");
        }
        if (!PAYMENT_METHODS.contains(request.getPaymentMethod())) {
            logger.error("Invalid payment method: {}", request.getPaymentMethod());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
        }

        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("customerId", request.getCustomerId());
        entity.put("pizzaSize", request.getPizzaSize());
        ArrayNode toppingsArray = entity.putArray("toppings");
        request.getToppings().forEach(toppingsArray::add);
        entity.put("deliveryAddress", request.getDeliveryAddress());
        entity.put("scheduledTime", request.getScheduledTime());
        entity.put("paymentMethod", request.getPaymentMethod());
        entity.put("paymentDetails", request.getPaymentDetails());

        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entity)
                .thenApply(id -> {
                    CreateOrderResponse resp = new CreateOrderResponse();
                    resp.setOrderId(id.toString());
                    resp.setStatus("created");
                    resp.setEstimatedDeliveryTime(null);
                    logger.info("Order created with orderId={}", id);
                    return ResponseEntity.status(HttpStatus.CREATED).body(resp);
                });
    }

    @GetMapping("/orders/{orderId}")
    public CompletableFuture<ResponseEntity<OrderStatusResponse>> getOrderStatus(@PathVariable @NotBlank String orderId) {
        logger.info("Retrieving status for orderId={}", orderId);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for orderId={}", orderId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        logger.error("Order not found for orderId={}", orderId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
                    }
                    OrderStatusResponse resp = new OrderStatusResponse();
                    resp.setOrderId(orderId);
                    resp.setStatus(item.path("status").asText("unknown").toLowerCase());
                    resp.setPizzaSize(item.path("pizzaSize").asText(null));
                    JsonNode toppingsNode = item.path("toppings");
                    if (toppingsNode.isArray()) {
                        resp.setToppings(toppingsNode.findValuesAsText(null));
                    } else {
                        resp.setToppings(List.of());
                    }
                    resp.setDeliveryAddress(item.path("deliveryAddress").asText(null));
                    resp.setEstimatedDeliveryTime(item.path("estimatedDeliveryTime").asText(null));
                    logger.info("Order status retrieved for orderId={}", orderId);
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
        logger.error("Handling exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse err = new ErrorResponse();
        err.setError(ex.getStatusCode().toString());
        err.setMessage(ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    // DTO classes
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

        // Getters and setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getPizzaSize() { return pizzaSize; }
        public void setPizzaSize(String pizzaSize) { this.pizzaSize = pizzaSize; }
        public List<String> getToppings() { return toppings; }
        public void setToppings(List<String> toppings) { this.toppings = toppings; }
        public String getDeliveryAddress() { return deliveryAddress; }
        public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
        public String getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getPaymentDetails() { return paymentDetails; }
        public void setPaymentDetails(String paymentDetails) { this.paymentDetails = paymentDetails; }
    }

    public static class CreateOrderResponse {
        private String orderId;
        private String status;
        private String estimatedDeliveryTime;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
        public void setEstimatedDeliveryTime(String estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
    }

    public static class OrderStatusResponse {
        private String orderId;
        private String status;
        private String pizzaSize;
        private List<String> toppings;
        private String deliveryAddress;
        private String estimatedDeliveryTime;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPizzaSize() { return pizzaSize; }
        public void setPizzaSize(String pizzaSize) { this.pizzaSize = pizzaSize; }
        public List<String> getToppings() { return toppings; }
        public void setToppings(List<String> toppings) { this.toppings = toppings; }
        public String getDeliveryAddress() { return deliveryAddress; }
        public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
        public String getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
        public void setEstimatedDeliveryTime(String estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
    }

    public static class AvailableOptionsResponse {
        private List<String> sizes;
        private List<String> toppings;
        private List<String> paymentMethods;

        public List<String> getSizes() { return sizes; }
        public void setSizes(List<String> sizes) { this.sizes = sizes; }
        public List<String> getToppings() { return toppings; }
        public void setToppings(List<String> toppings) { this.toppings = toppings; }
        public List<String> getPaymentMethods() { return paymentMethods; }
        public void setPaymentMethods(List<String> paymentMethods) { this.paymentMethods = paymentMethods; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}