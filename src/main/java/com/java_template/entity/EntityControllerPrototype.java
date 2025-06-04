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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/entity")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for orders, keyed by UUID orderId
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    // In-memory store for hourly reports keyed by ISO hour string (e.g. "2024-06-01T15:00:00Z")
    private final Map<String, HourlyReport> hourlyReports = new ConcurrentHashMap<>();

    /**
     * Simulate orders continuously in background.
     * Here, we just simulate a batch on startup for demo purposes.
     */
    @PostConstruct
    public void startSimulation() {
        logger.info("Starting initial order simulation batch...");
        simulateOrdersBatch(50);
        // TODO: Replace with proper scheduled or async continuous simulation
    }

    /**
     * Endpoint to manually trigger a batch simulation of orders.
     */
    @PostMapping("/simulateOrders")
    public ResponseEntity<Map<String, Object>> simulateOrders(
            @RequestParam(defaultValue = "20") int count) {
        logger.info("Simulating {} orders on demand", count);
        simulateOrdersBatch(count);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Orders simulated successfully");
        resp.put("ordersCreated", count);
        return ResponseEntity.ok(resp);
    }

    /**
     * Endpoint to manually trigger hourly report generation.
     */
    @PostMapping("/generateHourlyReport")
    public ResponseEntity<Map<String, Object>> generateHourlyReport() {
        logger.info("Generating hourly report on demand");
        String hourKey = generateCurrentHourKey();

        List<Order> lastHourOrders = getOrdersFromLastHour();

        Map<String, BigDecimal> totalsByPair = lastHourOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.EXECUTED)
                .collect(Collectors.groupingBy(Order::getPair,
                        Collectors.mapping(Order::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        HourlyReport report = new HourlyReport(hourKey, totalsByPair);
        hourlyReports.put(hourKey, report);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Hourly report generated");
        resp.put("reportTimestamp", hourKey);
        return ResponseEntity.ok(resp);
    }

    /**
     * Endpoint to retrieve the latest hourly report.
     */
    @GetMapping("/hourlyReport/latest")
    public ResponseEntity<HourlyReport> getLatestHourlyReport() {
        Optional<String> latestHour = hourlyReports.keySet().stream()
                .max(String::compareTo);
        if (latestHour.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports available");
        }
        HourlyReport report = hourlyReports.get(latestHour.get());
        return ResponseEntity.ok(report);
    }

    /**
     * Endpoint to trigger sending the latest hourly report email asynchronously.
     */
    @PostMapping("/sendReportEmail")
    public ResponseEntity<Map<String, Object>> sendReportEmail() {
        logger.info("Received request to send latest report email");

        Optional<String> latestHour = hourlyReports.keySet().stream()
                .max(String::compareTo);
        if (latestHour.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports available to email");
        }

        HourlyReport report = hourlyReports.get(latestHour.get());

        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Replace with real email sending logic
                logger.info("Sending email with report for hour {}: {}", report.getReportTimestamp(), report.getTotalsByPair());
                Thread.sleep(1000); // simulate delay
                logger.info("Email sent successfully");
            } catch (InterruptedException e) {
                logger.error("Email sending interrupted", e);
                Thread.currentThread().interrupt();
            }
        });

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Email sending triggered asynchronously");
        resp.put("emailTimestamp", Instant.now().toString());
        return ResponseEntity.ok(resp);
    }

    // --- Helper methods ---

    private void simulateOrdersBatch(int count) {
        Random rnd = new Random();
        List<String> pairs = Arrays.asList("BTC-USD", "ETH-USD", "XRP-USD");
        List<String> users = Arrays.asList("user1", "user2", "user3", "user4");

        for (int i = 0; i < count; i++) {
            Order order = new Order();
            order.setOrderId(UUID.randomUUID());
            order.setTimestamp(Instant.now().minusSeconds(rnd.nextInt(3600))); // random up to last hour
            order.setPrice(BigDecimal.valueOf(1000 + rnd.nextDouble() * 50000).setScale(2, BigDecimal.ROUND_HALF_UP));
            order.setAmount(BigDecimal.valueOf(0.01 + rnd.nextDouble() * 5).setScale(4, BigDecimal.ROUND_HALF_UP));
            order.setPair(pairs.get(rnd.nextInt(pairs.size())));
            order.setSide(rnd.nextBoolean() ? OrderSide.BUY : OrderSide.SELL);
            order.setStatus(rnd.nextDouble() < 0.8 ? OrderStatus.EXECUTED : OrderStatus.REJECTED); // 80% executed
            order.setUserId(users.get(rnd.nextInt(users.size())));

            orders.put(order.getOrderId(), order);
        }
        logger.info("Simulated {} orders", count);
    }

    private List<Order> getOrdersFromLastHour() {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        return orders.values().stream()
                .filter(o -> o.getTimestamp().isAfter(oneHourAgo))
                .collect(Collectors.toList());
    }

    private String generateCurrentHourKey() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        return nowUtc.toString();
    }

    // --- Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("status", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        Map<String, String> error = new HashMap<>();
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal Server Error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Data models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Order {
        private UUID orderId;
        private BigDecimal price;
        private String pair;
        private BigDecimal amount;
        private Instant timestamp;
        private OrderStatus status;
        private OrderSide side;
        private String userId;
    }

    enum OrderStatus {
        REJECTED,
        EXECUTED
    }

    enum OrderSide {
        BUY,
        SELL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class HourlyReport {
        private String reportTimestamp;
        private Map<String, BigDecimal> totalsByPair;
    }
}
```
