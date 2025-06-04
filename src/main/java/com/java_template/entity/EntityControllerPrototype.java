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
import org.springframework.validation.annotation.Validated;
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
import jakarta.validation.constraints.Min;

@Slf4j
@Validated
@RestController
@RequestMapping("/entity")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, HourlyReport> hourlyReports = new ConcurrentHashMap<>();

    @PostConstruct
    public void startSimulation() {
        logger.info("Starting initial order simulation batch...");
        simulateOrdersBatch(50);
        // TODO: Replace with proper scheduled or async continuous simulation
    }

    @PostMapping("/simulateOrders")
    public ResponseEntity<Map<String, Object>> simulateOrders(
        @RequestParam(defaultValue = "20") @Min(1) int count) {
        logger.info("Simulating {} orders on demand", count);
        simulateOrdersBatch(count);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Orders simulated successfully");
        resp.put("ordersCreated", count);
        return ResponseEntity.ok(resp);
    }

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

    @GetMapping("/hourlyReport/latest")
    public ResponseEntity<HourlyReport> getLatestHourlyReport() {
        Optional<String> latestHour = hourlyReports.keySet().stream().max(String::compareTo);
        if (latestHour.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports available");
        }
        return ResponseEntity.ok(hourlyReports.get(latestHour.get()));
    }

    @PostMapping("/sendReportEmail")
    public ResponseEntity<Map<String, Object>> sendReportEmail() {
        logger.info("Received request to send latest report email");
        Optional<String> latestHour = hourlyReports.keySet().stream().max(String::compareTo);
        if (latestHour.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports available to email");
        }
        HourlyReport report = hourlyReports.get(latestHour.get());
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Replace with real email sending logic
                logger.info("Sending email with report for hour {}: {}", report.getReportTimestamp(), report.getTotalsByPair());
                Thread.sleep(1000);
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

    private void simulateOrdersBatch(int count) {
        Random rnd = new Random();
        List<String> pairs = Arrays.asList("BTC-USD", "ETH-USD", "XRP-USD");
        List<String> users = Arrays.asList("user1", "user2", "user3", "user4");
        for (int i = 0; i < count; i++) {
            Order order = new Order();
            order.setOrderId(UUID.randomUUID());
            order.setTimestamp(Instant.now().minusSeconds(rnd.nextInt(3600)));
            order.setPrice(BigDecimal.valueOf(1000 + rnd.nextDouble() * 50000).setScale(2, BigDecimal.ROUND_HALF_UP));
            order.setAmount(BigDecimal.valueOf(0.01 + rnd.nextDouble() * 5).setScale(4, BigDecimal.ROUND_HALF_UP));
            order.setPair(pairs.get(rnd.nextInt(pairs.size())));
            order.setSide(rnd.nextBoolean() ? OrderSide.BUY : OrderSide.SELL);
            order.setStatus(rnd.nextDouble() < 0.8 ? OrderStatus.EXECUTED : OrderStatus.REJECTED);
            order.setUserId(users.get(rnd.nextInt(users.size())));
            orders.put(order.getOrderId(), order);
        }
        logger.info("Simulated {} orders", count);
    }

    private List<Order> getOrdersFromLastHour() {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        return orders.values().stream().filter(o -> o.getTimestamp().isAfter(oneHourAgo)).collect(Collectors.toList());
    }

    private String generateCurrentHourKey() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        return nowUtc.toString();
    }

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