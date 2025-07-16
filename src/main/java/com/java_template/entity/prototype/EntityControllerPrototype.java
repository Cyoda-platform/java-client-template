package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final List<String> AVAILABLE_SIZES = List.of("small", "medium", "large");
    private static final List<String> AVAILABLE_TOPPINGS = List.of("pepperoni", "mushrooms", "onions", "sausage", "bacon");
    private static final List<String> PAYMENT_METHODS = List.of("credit_card", "paypal", "cash");

    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        logger.info("Creating order for customerId={}", request.getCustomerId());
        if (!AVAILABLE_SIZES.contains(request.getPizzaSize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pizza size");
        }
        if (request.getToppings().isEmpty() || !AVAILABLE_TOPPINGS.containsAll(request.getToppings())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid toppings");
        }
        if (!PAYMENT_METHODS.contains(request.getPaymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
        }
        boolean paymentOk = mockValidatePayment(request.getPaymentMethod(), request.getPaymentDetails());
        if (!paymentOk) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment validation failed");
        }
        Instant eta = Instant.now().plusSeconds(30 * 60);
        String orderId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setPizzaSize(request.getPizzaSize());
        order.setToppings(request.getToppings());
        order.setDeliveryAddress(request.getDeliveryAddress());
        order.setScheduledTime(request.getScheduledTime());
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentDetails(request.getPaymentDetails());
        order.setStatus(OrderStatus.CREATED);
        order.setEstimatedDeliveryTime(eta);
        orders.put(orderId, order);
        CreateOrderResponse resp = new CreateOrderResponse();
        resp.setOrderId(orderId);
        resp.setStatus(order.getStatus().name().toLowerCase());
        resp.setEstimatedDeliveryTime(eta.toString());
        // TODO: fire-and-forget processing via CompletableFuture.runAsync(...)
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable @NotBlank String orderId) {
        logger.info("Retrieving status for orderId={}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        OrderStatusResponse resp = new OrderStatusResponse();
        resp.setOrderId(order.getOrderId());
        resp.setStatus(order.getStatus().name().toLowerCase());
        resp.setPizzaSize(order.getPizzaSize());
        resp.setToppings(order.getToppings());
        resp.setDeliveryAddress(order.getDeliveryAddress());
        resp.setEstimatedDeliveryTime(order.getEstimatedDeliveryTime().toString());
        return ResponseEntity.ok(resp);
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

    private boolean mockValidatePayment(String method, String details) {
        logger.info("Mock validate payment method={}", method);
        // TODO: replace with real payment gateway integration
        return true;
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

    private static class Order {
        private String orderId;
        private String customerId;
        private String pizzaSize;
        private List<String> toppings;
        private String deliveryAddress;
        private String scheduledTime;
        private String paymentMethod;
        private String paymentDetails;
        private OrderStatus status;
        private Instant estimatedDeliveryTime;
    }

    private enum OrderStatus {
        CREATED, PREPARING, BAKING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED
    }
}