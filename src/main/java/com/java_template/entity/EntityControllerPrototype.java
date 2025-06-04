```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Fixed pairs as per user requirement
    private static final List<String> PAIRS = List.of("BTC/USD", "ETH/USD");

    // Side options
    private static final List<String> SIDES = List.of("buy", "sell");

    // Store orders in a thread-safe map keyed by UUID (order id)
    private final ConcurrentLinkedQueue<Order> orders = new ConcurrentLinkedQueue<>();

    // Executor for async tasks like email sending
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Scheduled executor for continuous order generation
    private ScheduledExecutorService orderGeneratorScheduler;

    // Flag to indicate if generation is running
    private volatile boolean generatingOrders = false;

    @PostConstruct
    public void init() {
        // Start continuous order generation on app startup
        startOrderGeneration();
    }

    private void startOrderGeneration() {
        if (generatingOrders) {
            logger.info("Order generation already running.");
            return;
        }
        generatingOrders = true;
        orderGeneratorScheduler = Executors.newSingleThreadScheduledExecutor();
        // Every 1 second, generate 10 orders
        orderGeneratorScheduler.scheduleAtFixedRate(() -> {
            try {
                generateRandomOrders(10);
            } catch (Exception e) {
                logger.error("Error generating random orders", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
        logger.info("Started continuous order generation: 10 orders/second");
    }

    private void generateRandomOrders(int count) {
        Random rnd = new Random();
        for (int i = 0; i < count; i++) {
            String side = SIDES.get(rnd.nextInt(SIDES.size()));
            String pair = PAIRS.get(rnd.nextInt(PAIRS.size()));
            double price = generatePriceForPair(pair, rnd);
            double amount = 0.01 + (rnd.nextDouble() * 10); // random amount 0.01 to 10
            Instant timestamp = Instant.now();
            Order order = new Order(UUID.randomUUID().toString(), side, price, amount, pair, timestamp);
            orders.add(order);
            logger.debug("Generated order: {}", order);
        }
        logger.info("Generated {} random orders", count);
    }

    // Simple mock price generation based on pair
    private double generatePriceForPair(String pair, Random rnd) {
        // TODO: Replace with real price feed if needed
        switch (pair) {
            case "BTC/USD":
                return 29000 + rnd.nextDouble() * 2000; // 29k - 31k
            case "ETH/USD":
                return 1800 + rnd.nextDouble() * 400;  // 1800 - 2200
            default:
                return 100 + rnd.nextDouble() * 1000;
        }
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<SummaryResponse> getOrdersSummary() {
        try {
            // Aggregate total amount and average price per pair
            Map<String, List<Order>> ordersByPair = orders.stream()
                    .collect(Collectors.groupingBy(Order::getPair));

            List<PairSummary> summaryList = new ArrayList<>();

            for (String pair : PAIRS) {
                List<Order> pairOrders = ordersByPair.getOrDefault(pair, Collections.emptyList());
                double totalAmount = pairOrders.stream().mapToDouble(Order::getAmount).sum();
                double avgPrice = pairOrders.isEmpty() ? 0.0 :
                        pairOrders.stream().mapToDouble(Order::getPrice).average().orElse(0.0);
                summaryList.add(new PairSummary(pair, totalAmount, avgPrice));
            }

            SummaryResponse response = new SummaryResponse(summaryList);
            logger.info("Returning orders summary");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving orders summary", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving summary");
        }
    }

    @PostMapping("/report/send")
    public ResponseEntity<ReportResponse> sendHourlyReport() {
        // Fire-and-forget email sending simulation
        CompletableFuture.runAsync(() -> {
            try {
                sendEmailReport();
            } catch (Exception e) {
                logger.error("Failed to send hourly report email", e);
            }
        }, executor);

        logger.info("Triggered hourly report email sending");
        return ResponseEntity.ok(new ReportResponse("email_sent", "Hourly report email has been sent"));
    }

    // Simulated email sending method
    private void sendEmailReport() {
        // Aggregate data same as summary
        Map<String, List<Order>> ordersByPair = orders.stream()
                .collect(Collectors.groupingBy(Order::getPair));

        StringBuilder emailContent = new StringBuilder("Hourly Orders Summary:\n");
        for (String pair : PAIRS) {
            List<Order> pairOrders = ordersByPair.getOrDefault(pair, Collections.emptyList());
            double totalAmount = pairOrders.stream().mapToDouble(Order::getAmount).sum();
            double avgPrice = pairOrders.isEmpty() ? 0.0 :
                    pairOrders.stream().mapToDouble(Order::getPrice).average().orElse(0.0);
            emailContent.append(String.format("- %s: total amount=%.4f, avg price=%.2f%n", pair, totalAmount, avgPrice));
        }

        // TODO: Replace this with real email sending logic
        logger.info("Sending email report:\n{}", emailContent.toString());
    }

    // Minimal error handler for demonstration
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", ex.getStatusCode().toString());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // --- Data classes ---

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Order {
        private String id;
        private String side;      // "buy" or "sell"
        private double price;
        private double amount;
        private String pair;
        private Instant timestamp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class PairSummary {
        private String pair;
        private double totalAmount;
        private double averagePrice;
    }

    @Data
    @AllArgsConstructor
    static class SummaryResponse {
        private List<PairSummary> summary;
    }

    @Data
    @AllArgsConstructor
    static class ReportResponse {
        private String status;
        private String message;
    }
}
```
