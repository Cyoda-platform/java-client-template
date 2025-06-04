package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Validated
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> PAIRS = List.of("BTC/USD", "ETH/USD");
    private static final List<String> SIDES = List.of("buy", "sell");
    private final ConcurrentLinkedQueue<Order> orders = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService orderGeneratorScheduler;
    private volatile boolean generatingOrders = false;

    @PostConstruct
    public void init() {
        startOrderGeneration();
    }

    private void startOrderGeneration() {
        if (generatingOrders) {
            logger.info("Order generation already running.");
            return;
        }
        generatingOrders = true;
        orderGeneratorScheduler = Executors.newSingleThreadScheduledExecutor();
        orderGeneratorScheduler.scheduleAtFixedRate(() -> {
            try { generateRandomOrders(10); }
            catch (Exception e) { logger.error("Error generating random orders", e); }
        }, 0, 1, TimeUnit.SECONDS);
        logger.info("Started continuous order generation: 10 orders/second");
    }

    private void generateRandomOrders(int count) {
        Random rnd = new Random();
        for (int i = 0; i < count; i++) {
            String side = SIDES.get(rnd.nextInt(SIDES.size()));
            String pair = PAIRS.get(rnd.nextInt(PAIRS.size()));
            double price = generatePriceForPair(pair, rnd);
            double amount = 0.01 + (rnd.nextDouble() * 10);
            Instant timestamp = Instant.now();
            Order order = new Order(UUID.randomUUID().toString(), side, price, amount, pair, timestamp);
            orders.add(order);
            logger.debug("Generated order: {}", order);
        }
        logger.info("Generated {} random orders", count);
    }

    private double generatePriceForPair(String pair, Random rnd) {
        switch (pair) {
            case "BTC/USD": return 29000 + rnd.nextDouble() * 2000;
            case "ETH/USD": return 1800 + rnd.nextDouble() * 400;
            default: return 100 + rnd.nextDouble() * 1000;
        }
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<SummaryResponse> getOrdersSummary(
            @RequestParam(required = false)
            @Pattern(regexp = "BTC/USD|ETH/USD", message = "Pair must be BTC/USD or ETH/USD")
            String pairFilter) {
        try {
            Map<String, List<Order>> grouped = orders.stream()
                    .filter(o -> pairFilter == null || o.getPair().equals(pairFilter))
                    .collect(Collectors.groupingBy(Order::getPair));
            List<PairSummary> summaryList = new ArrayList<>();
            for (String pair : PAIRS) {
                if (pairFilter != null && !pair.equals(pair)) continue;
                List<Order> list = grouped.getOrDefault(pair, Collections.emptyList());
                double total = list.stream().mapToDouble(Order::getAmount).sum();
                double avg = list.isEmpty() ? 0.0 : list.stream().mapToDouble(Order::getPrice).average().orElse(0.0);
                summaryList.add(new PairSummary(pair, total, avg));
            }
            return ResponseEntity.ok(new SummaryResponse(summaryList));
        } catch (Exception e) {
            logger.error("Error retrieving orders summary", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving summary");
        }
    }

    @PostMapping("/report/send")
    public ResponseEntity<ReportResponse> sendHourlyReport(@RequestBody @Valid ReportRequest request) {
        String toEmail = request.getEmail();
        CompletableFuture.runAsync(() -> {
            try { sendEmailReport(toEmail); }
            catch (Exception e) { logger.error("Failed to send report to {}", toEmail, e); }
        }, executor);
        return ResponseEntity.ok(new ReportResponse("email_sent", "Report email sent to " + toEmail));
    }

    private void sendEmailReport(String toEmail) {
        Map<String, List<Order>> grouped = orders.stream()
                .collect(Collectors.groupingBy(Order::getPair));
        StringBuilder content = new StringBuilder("Hourly Orders Summary:\n");
        for (String pair : PAIRS) {
            List<Order> list = grouped.getOrDefault(pair, Collections.emptyList());
            double total = list.stream().mapToDouble(Order::getAmount).sum();
            double avg = list.isEmpty() ? 0.0 : list.stream().mapToDouble(Order::getPrice).average().orElse(0.0);
            content.append(String.format("- %s: total=%.4f, avg=%.2f%n", pair, total, avg));
        }
        // TODO: integrate real email service and use 'toEmail'
        logger.info("Sending email to {}: \n{}", toEmail, content);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleException(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getReason());
        err.put("status", ex.getStatusCode().toString());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    static class Order {
        private String id;
        private String side;
        private double price;
        private double amount;
        private String pair;
        private Instant timestamp;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    static class PairSummary {
        private String pair;
        private double totalAmount;
        private double averagePrice;
    }

    @Data @AllArgsConstructor
    static class SummaryResponse {
        private List<PairSummary> summary;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    static class ReportRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data @AllArgsConstructor
    static class ReportResponse {
        private String status;
        private String message;
    }
}