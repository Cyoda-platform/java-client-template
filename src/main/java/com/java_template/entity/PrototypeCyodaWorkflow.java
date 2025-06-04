```java
package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component
@RequestMapping("/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private static final String ENTITY_NAME = "Order";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void startSimulation() {
        logger.info("Starting initial order simulation batch...");
        simulateOrdersBatch(50);
    }

    @Scheduled(fixedRate = 10000)
    public void simulateOrdersPeriodically() {
        logger.info("Periodic order simulation triggered");
        simulateOrdersBatch(10);
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void generateReportAndSendEmail() throws ExecutionException, InterruptedException {
        logger.info("Hourly report generation and email sending triggered");
        String hourKey = generateCurrentHourKey();

        List<Order> lastHourOrders = getOrdersFromLastHour();

        Map<String, BigDecimal> totalsByPair = lastHourOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.EXECUTED)
                .collect(Collectors.groupingBy(Order::getPair,
                        Collectors.mapping(Order::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        HourlyReport report = new HourlyReport(hourKey, totalsByPair);
        // Save report as entity, add workflow function processHourlyReport
        entityService.addItem("HourlyReport", ENTITY_VERSION, report, this::processHourlyReport).get();

        sendReportEmail(report);
    }

    private void simulateOrdersBatch(int count) {
        Random rnd = new Random();
        List<String> pairs = Arrays.asList("BTC-USD", "ETH-USD", "XRP-USD");
        List<String> users = Arrays.asList("user1", "user2", "user3", "user4");

        List<Order> batch = new ArrayList<>();

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
            batch.add(order);
        }

        try {
            // Use addItems with workflow function processOrder
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, batch, this::processOrder);
            idsFuture.get(); // wait for completion
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error adding batch orders", e);
            Thread.currentThread().interrupt();
        }
        logger.info("Simulated {} orders", count);
    }

    private List<Order> getOrdersFromLastHour() throws ExecutionException, InterruptedException {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);

        ArrayNode items = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();

        List<Order> orders = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            ObjectNode obj = (ObjectNode) items.get(i);
            // Map ObjectNode to Order
            Order order = JsonUtil.fromObjectNode(obj, Order.class);
            if (order.getTimestamp().isAfter(oneHourAgo)) {
                orders.add(order);
            }
        }
        return orders;
    }

    private String generateCurrentHourKey() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        return nowUtc.toString();
    }

    private void sendReportEmail(HourlyReport report) {
        // TODO: Replace with real email sending implementation
        logger.info("Sending email with report for hour {}: {}", report.getReportTimestamp(), report.getTotalsByPair());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.error("Email sending interrupted", e);
            Thread.currentThread().interrupt();
        }
        logger.info("Email sent successfully");
    }

    /**
     * Workflow function for Order entity.
     * It receives an Order object and returns the possibly modified Order asynchronously.
     * You can update state or add/get entities of other models here.
     */
    private CompletableFuture<Order> processOrder(Order order) {
        // Example: Add a simple log and return as is.
        logger.debug("Processing Order workflow for orderId: {}", order.getOrderId());

        // Example modification: if order status is REJECTED, add some flag or note (not in current model)
        // For demo, just return the order unchanged asynchronously.
        return CompletableFuture.completedFuture(order);
    }

    /**
     * Workflow function for HourlyReport entity.
     * It receives an HourlyReport object and returns the possibly modified HourlyReport asynchronously.
     */
    private CompletableFuture<HourlyReport> processHourlyReport(HourlyReport report) {
        logger.debug("Processing HourlyReport workflow for timestamp: {}", report.getReportTimestamp());
        // No modifications for now, just return as is.
        return CompletableFuture.completedFuture(report);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        @JsonIgnore
        private UUID technicalId; // stored as technicalId in entityService
        private UUID orderId;
        private BigDecimal price;
        private String pair;
        private BigDecimal amount;
        private Instant timestamp;
        private OrderStatus status;
        private OrderSide side;
        private String userId;
    }

    public enum OrderStatus {
        REJECTED,
        EXECUTED
    }

    public enum OrderSide {
        BUY,
        SELL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyReport {
        private String reportTimestamp;
        private Map<String, BigDecimal> totalsByPair;
    }
}

// Utility class to convert ObjectNode to POJO using Jackson
package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T fromObjectNode(ObjectNode node, Class<T> clazz) {
        try {
            return mapper.treeToValue(node, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```
---

### Explanation

- Added a new parameter `workflow` of type `Function<T, CompletableFuture<T>>` (or equivalent) in all `entityService.addItem` and `addItems` calls.
- Created two workflow functions:
  - `processOrder(Order order)` for the `Order` entity.
  - `processHourlyReport(HourlyReport report)` for the `HourlyReport` entity.
- These workflow functions return a `CompletableFuture` of the entity after applying any processing.
- Updated calls to `entityService.addItem` and `addItems` to pass the corresponding workflow function.
- Workflow functions currently just log and return the entity unchanged asynchronously but can be extended to modify the entity or interact with other entities.

This matches the new expected signature of `entityService.addItem` and the contract described.