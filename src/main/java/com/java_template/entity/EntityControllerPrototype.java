package com.java_template.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EntityControllerPrototype {

    // In-memory store for orders, keyed by UUID orderId
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    // In-memory store for hourly reports keyed by ISO hour string (e.g. "2024-06-01T15:00:00Z")
    private final Map<String, HourlyReport> hourlyReports = new ConcurrentHashMap<>();

    /**
     * Simulate orders continuously in background.
     * Here, we simulate a batch on startup for demo purposes.
     */
    @PostConstruct
    public void startSimulation() {
        logger.info("Starting initial order simulation batch...");
        simulateOrdersBatch(50);
    }

    /**
     * Scheduled task to simulate orders every 10 seconds.
     * Adjust the fixedRate as needed.
     */
    @Scheduled(fixedRate = 10000)
    public void simulateOrdersPeriodically() {
        logger.info("Periodic order simulation triggered");
        simulateOrdersBatch(10);
    }

    /**
     * Scheduled task to generate hourly report and send email every hour on the hour.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void generateReportAndSendEmail() {
        logger.info("Hourly report generation and email sending triggered");
        String hourKey = generateCurrentHourKey();

        List<Order> lastHourOrders = getOrdersFromLastHour();

        Map<String, BigDecimal> totalsByPair = lastHourOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.EXECUTED)
                .collect(Collectors.groupingBy(Order::getPair,
                        Collectors.mapping(Order::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        HourlyReport report = new HourlyReport(hourKey, totalsByPair);
        hourlyReports.put(hourKey, report);

        sendReportEmail(report);
    }

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

    private void sendReportEmail(HourlyReport report) {
        // TODO: Replace with real email sending implementation
        logger.info("Sending email with report for hour {}: {}", report.getReportTimestamp(), report.getTotalsByPair());
        // Simulate email sending delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.error("Email sending interrupted", e);
            Thread.currentThread().interrupt();
        }
        logger.info("Email sent successfully");
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
