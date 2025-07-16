package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mocked options - in real app, these might come from a DB or external service
    private static final List<String> AVAILABLE_SIZES = List.of("small", "medium", "large");
    private static final List<String> AVAILABLE_TOPPINGS = List.of("pepperoni", "mushrooms", "onions", "sausage", "bacon");
    private static final List<String> PAYMENT_METHODS = List.of("credit_card", "paypal", "cash");

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a new pizza order
     */
    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        logger.info("Received order creation request for customerId={}", request.getCustomerId());

        // Validate pizza size and toppings
        if (!AVAILABLE_SIZES.contains(request.getPizza().getSize())) {
            logger.error("Invalid pizza size requested: {}", request.getPizza().getSize());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pizza size");
        }

        for (String topping : request.getPizza().getToppings()) {
            if (!AVAILABLE_TOPPINGS.contains(topping)) {
                logger.error("Invalid pizza topping requested: {}", topping);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pizza topping: " + topping);
            }
        }

        if (!PAYMENT_METHODS.contains(request.getPayment().getMethod())) {
            logger.error("Invalid payment method requested: {}", request.getPayment().getMethod());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
        }

        // TODO: Call external payment gateway API to validate payment
        // Here we mock payment validation and delivery time calculation
        boolean paymentSuccess = mockValidatePayment(request.getPayment());
        if (!paymentSuccess) {
            logger.error("Payment validation failed for customerId={}", request.getCustomerId());
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment validation failed");
        }

        // Mock estimated delivery time calculation (e.g., 30 minutes from now)
        Instant estimatedDelivery = Instant.now().plusSeconds(30 * 60);

        // Create order ID
        String orderId = UUID.randomUUID().toString();

        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setPizza(request.getPizza());
        order.setDelivery(request.getDelivery());
        order.setPayment(request.getPayment());
        order.setStatus(OrderStatus.CREATED);
        order.setEstimatedDeliveryTime(estimatedDelivery);

        orders.put(orderId, order);

        logger.info("Order created successfully with orderId={}", orderId);

        CreateOrderResponse response = new CreateOrderResponse();
        response.setOrderId(orderId);
        response.setStatus(order.getStatus().name().toLowerCase());
        response.setEstimatedDeliveryTime(estimatedDelivery.toString());

        // TODO: Fire-and-forget order processing (e.g., baking, delivery tracking)
        // CompletableFuture.runAsync(() -> processOrder(order));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get the status and details of an existing order
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable("orderId") @NotBlank String orderId) {
        logger.info("Fetching order status for orderId={}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            logger.error("Order not found for orderId={}", orderId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        OrderStatusResponse response = new OrderStatusResponse();
        response.setOrderId(order.getOrderId());
        response.setStatus(order.getStatus().name().toLowerCase());
        response.setPizza(order.getPizza());
        response.setDelivery(order.getDelivery());
        response.setEstimatedDeliveryTime(order.getEstimatedDeliveryTime().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * List available pizza sizes, toppings and payment methods
     */
    @GetMapping("/options")
    public ResponseEntity<AvailableOptionsResponse> getAvailableOptions() {
        logger.info("Fetching available pizza options");
        AvailableOptionsResponse response = new AvailableOptionsResponse();
        response.setSizes(AVAILABLE_SIZES);
        response.setToppings(AVAILABLE_TOPPINGS);
        response.setPaymentMethods(PAYMENT_METHODS);
        return ResponseEntity.ok(response);
    }

    // --- Mock methods ---

    private boolean mockValidatePayment(Payment payment) {
        logger.info("Mock validating payment method={}", payment.getMethod());
        // TODO: Replace with real payment gateway integration
        return true;
    }

    // --- Data classes ---

    @Data
    public static class CreateOrderRequest {
        @NotBlank
        private String customerId;
        @Valid
        private Pizza pizza;
        @Valid
        private Delivery delivery;
        @Valid
        private Payment payment;
    }

    @Data
    public static class Pizza {
        @NotBlank
        private String size;
        private List<String> toppings = new ArrayList<>();
    }

    @Data
    public static class Delivery {
        @NotBlank
        private String address;
        private String scheduledTime; // ISO8601 string, optional
    }

    @Data
    public static class Payment {
        @NotBlank
        private String method;
        private Map<String, String> details = new HashMap<>();
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
        private Pizza pizza;
        private Delivery delivery;
        private String estimatedDeliveryTime;
    }

    @Data
    public static class AvailableOptionsResponse {
        private List<String> sizes;
        private List<String> toppings;
        private List<String> paymentMethods;
    }

    @Data
    public static class Order {
        private String orderId;
        private String customerId;
        private Pizza pizza;
        private Delivery delivery;
        private Payment payment;
        private OrderStatus status;
        private Instant estimatedDeliveryTime;
    }

    public enum OrderStatus {
        CREATED,
        PREPARING,
        BAKING,
        OUT_FOR_DELIVERY,
        DELIVERED,
        CANCELLED
    }

    // --- Minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: status={}, message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse();
        error.setError(ex.getStatusCode().toString());
        error.setMessage(ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;
    }

}