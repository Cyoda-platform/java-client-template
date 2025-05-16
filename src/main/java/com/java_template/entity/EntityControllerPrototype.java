package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Double> executedAmountsByPair = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRequest {
        @NotNull
        @Positive
        private Double price;

        @NotBlank
        @Pattern(regexp = "(?i)buy|sell")
        private String side;

        @NotNull
        @Positive
        private Double amount;

        @NotBlank
        private String pair;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private UUID orderId;
        private String status;
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
        private String status;
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
        private String status;
        private Instant createdAt;
    }

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/orders") // must be first
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest request) {
        log.info("Received new order request: {}", request);
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        if (request.getAmount() >= 10_000) {
            log.info("Order amount exceeds limit: {}", request.getAmount());
            Order rejectedOrder = new Order(orderId, request.getPrice(), request.getSide(), request.getAmount(), request.getPair(), "rejected", now);
            orders.put(orderId, rejectedOrder);
            return ResponseEntity.ok(new OrderResponse(orderId, "rejected", "Amount exceeds limit"));
        }

        Order newOrder = new Order(orderId, request.getPrice(), request.getSide(), request.getAmount(), request.getPair(), "pending", now);
        orders.put(orderId, newOrder);
        CompletableFuture.runAsync(() -> executeOrderWorkflow(newOrder));
        return ResponseEntity.ok(new OrderResponse(orderId, "executed", "Validation passed and executed"));
    }

    private void executeOrderWorkflow(Order order) {
        log.info("Executing order workflow for orderId={}", order.getOrderId());
        try {
            Thread.sleep(100); // simulate execution delay
            order.setStatus("executed");
            orders.put(order.getOrderId(), order);
            executedAmountsByPair.merge(order.getPair(), order.getAmount(), Double::sum);
            log.info("Order executed successfully: orderId={}", order.getOrderId());
        } catch (InterruptedException e) {
            log.error("Execution interrupted for orderId={}", order.getOrderId(), e);
            Thread.currentThread().interrupt();
        }
    }

    @GetMapping("/orders/{orderId}") // must be first
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable @NotNull UUID orderId) {
        log.info("Fetching order status for orderId={}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        OrderStatusResponse response = new OrderStatusResponse(
                order.getOrderId(), order.getPrice(), order.getSide(),
                order.getAmount(), order.getPair(), order.getStatus(), order.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/executions") // must be first
    public ResponseEntity<ExecutionReportResponse> getExecutionReport() {
        log.info("Generating execution report");
        List<ExecutionSummary> summaries = new ArrayList<>();
        executedAmountsByPair.forEach((pair, total) -> summaries.add(new ExecutionSummary(pair, total)));
        ExecutionReportResponse report = new ExecutionReportResponse(Instant.now(), summaries);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}