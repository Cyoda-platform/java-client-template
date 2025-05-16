```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Double> executedAmountsByPair = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRequest {
        private Double price;
        private String side; // "buy" or "sell"
        private Double amount;
        private String pair;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private UUID orderId;
        private String status; // executed or rejected
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusResponse {
        private UUID orderId;
        private Double price;
        private String side;
        private Double amount;
        private String pair;
        private String status; // executed, rejected, pending
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ExecutionSummary {
        private String pair;
        private Double totalAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionReportResponse {
        private Instant reportGeneratedAt;
        private List<ExecutionSummary> executions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Order {
        private UUID orderId;
        private Double price;
        private String side;
        private Double amount;
        private String pair;
        private String status; // executed, rejected, pending
        private Instant createdAt;
    }

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        log.info("Received new order request: {}", request);
        // Basic validation for required fields
        if (request.getPrice() == null || request.getSide() == null || request.getAmount() == null || request.getPair() == null) {
            log.error("Validation failed: missing required fields");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields");
        }
        // Validate side
        if (!"buy".equalsIgnoreCase(request.getSide()) && !"sell".equalsIgnoreCase(request.getSide())) {
            log.error("Validation failed: invalid side {}", request.getSide());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid side, must be 'buy' or 'sell'");
        }

        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        // Validate amount < 10_000
        if (request.getAmount() >= 10_000) {
            log.info("Order amount exceeds limit: {}", request.getAmount());
            Order rejectedOrder = new Order(orderId, request.getPrice(), request.getSide(), request.getAmount(), request.getPair(), "rejected", now);
            orders.put(orderId, rejectedOrder);
            return ResponseEntity.ok(new OrderResponse(orderId, "rejected", "Amount exceeds limit"));
        }

        // Create pending order and store
        Order newOrder = new Order(orderId, request.getPrice(), request.getSide(), request.getAmount(), request.getPair(), "pending", now);
        orders.put(orderId, newOrder);

        // Fire-and-forget execution workflow
        CompletableFuture.runAsync(() -> executeOrderWorkflow(newOrder));

        return ResponseEntity.ok(new OrderResponse(orderId, "executed", "Validation passed and executed"));
    }

    private void executeOrderWorkflow(Order order) {
        log.info("Executing order workflow for orderId={}", order.getOrderId());
        try {
            // Emulate execution delay or logic - TODO: replace with real execution logic or external API call
            Thread.sleep(100); // simulate execution delay

            // Mark order as executed
            order.setStatus("executed");
            orders.put(order.getOrderId(), order);

            // Update executed amounts summary (thread-safe)
            executedAmountsByPair.merge(order.getPair(), order.getAmount(), Double::sum);

            log.info("Order executed successfully: orderId={}", order.getOrderId());
        } catch (InterruptedException e) {
            log.error("Execution interrupted for orderId={}", order.getOrderId(), e);
            Thread.currentThread().interrupt();
        }
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable UUID orderId) {
        log.info("Fetching order status for orderId={}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            log.error("Order not found: orderId={}", orderId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        OrderStatusResponse response = new OrderStatusResponse(
                order.getOrderId(),
                order.getPrice(),
                order.getSide(),
                order.getAmount(),
                order.getPair(),
                order.getStatus(),
                order.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/executions")
    public ResponseEntity<ExecutionReportResponse> getExecutionReport() {
        log.info("Generating execution report");
        List<ExecutionSummary> summaries = new ArrayList<>();
        executedAmountsByPair.forEach((pair, totalAmount) -> {
            summaries.add(new ExecutionSummary(pair, totalAmount));
        });
        ExecutionReportResponse report = new ExecutionReportResponse(Instant.now(), summaries);
        return ResponseEntity.ok(report);
    }

    // Basic Exception Handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
```