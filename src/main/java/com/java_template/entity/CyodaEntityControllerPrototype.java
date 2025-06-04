package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final List<String> PAIRS = List.of("BTC/USD", "ETH/USD");
    private static final List<String> SIDES = List.of("buy", "sell");

    @Resource
    private EntityService entityService;

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
            try {
                generateAndStoreRandomOrders(10);
            } catch (Exception e) {
                logger.error("Error generating random orders", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
        logger.info("Started continuous order generation: 10 orders/second");
    }

    private void generateAndStoreRandomOrders(int count) {
        Random rnd = new Random();
        List<Order> newOrders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String side = SIDES.get(rnd.nextInt(SIDES.size()));
            String pair = PAIRS.get(rnd.nextInt(PAIRS.size()));
            double price = generatePriceForPair(pair, rnd);
            double amount = 0.01 + (rnd.nextDouble() * 10);
            Instant timestamp = Instant.now();
            Order order = new Order(null, side, price, amount, pair, timestamp);
            newOrders.add(order);
            logger.debug("Generated order: {}", order);
        }
        // Store generated orders using entityService.addItems
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                "Order",
                ENTITY_VERSION,
                newOrders
        );
        idsFuture.whenComplete((ids, ex) -> {
            if (ex != null) {
                logger.error("Failed to store generated orders", ex);
            } else {
                logger.info("Stored {} new orders with technicalIds: {}", ids.size(), ids);
            }
        });
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

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("Order", ENTITY_VERSION);

        ArrayNode ordersNode = itemsFuture.join();

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < ordersNode.size(); i++) {
            ObjectNode obj = (ObjectNode) ordersNode.get(i);
            Order order = JsonUtils.nodeToOrder(obj);
            orders.add(order);
        }

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
    }

    @PostMapping("/report/send")
    public ResponseEntity<ReportResponse> sendHourlyReport(@RequestBody @Valid ReportRequest request) {
        String toEmail = request.getEmail();
        CompletableFuture.runAsync(() -> sendEmailReport(toEmail));
        return ResponseEntity.ok(new ReportResponse("email_sent", "Report email sent to " + toEmail));
    }

    private void sendEmailReport(String toEmail) {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("Order", ENTITY_VERSION);

        ArrayNode ordersNode = itemsFuture.join();

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < ordersNode.size(); i++) {
            ObjectNode obj = (ObjectNode) ordersNode.get(i);
            Order order = JsonUtils.nodeToOrder(obj);
            orders.add(order);
        }

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
    public static class Order {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String side;
        private double price;
        private double amount;
        private String pair;
        private Instant timestamp;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class PairSummary {
        private String pair;
        private double totalAmount;
        private double averagePrice;
    }

    @Data @AllArgsConstructor
    public static class SummaryResponse {
        private List<PairSummary> summary;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ReportRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data @AllArgsConstructor
    public static class ReportResponse {
        private String status;
        private String message;
    }

    // Helper utility class to convert ObjectNode to Order
    private static class JsonUtils {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static Order nodeToOrder(ObjectNode node) {
            try {
                Order order = mapper.treeToValue(node, Order.class);
                if (node.has("technicalId") && !node.get("technicalId").isNull()) {
                    order.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                }
                return order;
            } catch (Exception e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse order from entityService data");
            }
        }
    }
}