package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@Component
@RequestMapping("/cyoda-entity")
public class CyodaEntityController {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "Order";
    private static final String HOURLY_REPORT_ENTITY = "HourlyReport";

    private static final ObjectMapper mapper = new ObjectMapper();

    public CyodaEntityController(EntityService entityService) {
        this.entityService = entityService;
    }

    // Controller just calls simulateOrdersBatch with raw data and workflow function
    @PostConstruct
    public void startSimulation() {
        log.info("Starting initial order simulation batch...");
        simulateOrdersBatch(50);
    }

    @Scheduled(fixedRate = 10000)
    public void simulateOrdersPeriodically() {
        log.info("Periodic order simulation triggered");
        simulateOrdersBatch(10);
    }

    // Instead of generating report here, we just trigger an empty report entity.
    // The report generation logic moves to processHourlyReport workflow.
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void triggerHourlyReport() {
        log.info("Triggering hourly report generation");
        // Create empty report entity with timestamp key, workflow will fill details
        ObjectNode reportNode = mapper.createObjectNode();
        String hourKey = ZonedDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0).toString();
        reportNode.put("reportTimestamp", hourKey);
        // totalsByPair will be computed in workflow, so just create empty object now
        reportNode.putObject("totalsByPair");

        entityService.addItem(HOURLY_REPORT_ENTITY, ENTITY_VERSION, reportNode, this::processHourlyReport)
                .exceptionally(ex -> {
                    log.error("Failed to add HourlyReport entity", ex);
                    return null;
                });
    }

    private void simulateOrdersBatch(int count) {
        Random rnd = new Random();
        List<String> pairs = Arrays.asList("BTC-USD", "ETH-USD", "XRP-USD");
        List<String> users = Arrays.asList("user1", "user2", "user3", "user4");

        List<ObjectNode> batch = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ObjectNode orderNode = mapper.createObjectNode();
            orderNode.put("orderId", UUID.randomUUID().toString());
            orderNode.put("timestamp", Instant.now().minusSeconds(rnd.nextInt(3600)).toString());
            orderNode.put("price", BigDecimal.valueOf(1000 + rnd.nextDouble() * 50000).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
            orderNode.put("amount", BigDecimal.valueOf(0.01 + rnd.nextDouble() * 5).setScale(4, BigDecimal.ROUND_HALF_UP).toString());
            orderNode.put("pair", pairs.get(rnd.nextInt(pairs.size())));
            orderNode.put("side", rnd.nextBoolean() ? "BUY" : "SELL");
            orderNode.put("status", rnd.nextDouble() < 0.8 ? "EXECUTED" : "REJECTED");
            orderNode.put("userId", users.get(rnd.nextInt(users.size())));

            batch.add(orderNode);
        }

        // Add all orders with workflow processOrder
        entityService.addItems(ENTITY_NAME, ENTITY_VERSION, batch, this::processOrder)
                .exceptionally(ex -> {
                    log.error("Failed to add batch orders", ex);
                    return null;
                });
    }

    /**
     * Workflow function for Order entity.
     * Processes the order asynchronously before persistence.
     * Modifies entity as needed, can add/get other entities but not same entity model.
     */
    private CompletableFuture<ObjectNode> processOrder(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add a processing timestamp
                entity.put("processedTimestamp", Instant.now().toString());

                // If status REJECTED, add a rejection reason field
                if ("REJECTED".equals(entity.get("status").asText())) {
                    entity.put("rejectionReason", "Simulated rejection");
                }

                // Example: add supplementary entity of a different model (Notification)
                if ("EXECUTED".equals(entity.get("status").asText())) {
                    ObjectNode notification = mapper.createObjectNode();
                    notification.put("notificationId", UUID.randomUUID().toString());
                    notification.put("orderId", entity.get("orderId").asText());
                    notification.put("message", "Order executed successfully");
                    notification.put("timestamp", Instant.now().toString());

                    // Add notification entity asynchronously, fire and forget
                    entityService.addItem("Notification", ENTITY_VERSION, notification, this::processNotification)
                            .exceptionally(ex -> {
                                log.error("Failed to add Notification entity", ex);
                                return null;
                            });
                }

                log.debug("Order processed in workflow: {}", entity.get("orderId").asText());
                return entity;
            } catch (Exception e) {
                log.error("Error processing order workflow", e);
                return entity;
            }
        });
    }

    /**
     * Workflow function for Notification entity.
     * Just pass through for now, could add more logic or enrich notification.
     */
    private CompletableFuture<ObjectNode> processNotification(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Workflow function for HourlyReport entity.
     * Generates the report by fetching orders from last hour,
     * calculating totals, updating the report entity,
     * and sending an email asynchronously.
     */
    private CompletableFuture<ObjectNode> processHourlyReport(ObjectNode reportEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String reportTimestamp = reportEntity.get("reportTimestamp").asText();
                log.info("Processing HourlyReport workflow for hour: {}", reportTimestamp);

                // Parse reportTimestamp to Instant for filtering
                Instant reportInstant = Instant.parse(reportTimestamp);

                // Get all orders to calculate report
                ArrayNode ordersArray;
                try {
                    ordersArray = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
                } catch (Exception e) {
                    log.error("Failed to fetch orders for report generation", e);
                    return reportEntity;
                }

                // Filter orders within the hour [reportInstant, reportInstant + 1 hour)
                List<ObjectNode> relevantOrders = new ArrayList<>();
                for (int i = 0; i < ordersArray.size(); i++) {
                    ObjectNode orderNode = (ObjectNode) ordersArray.get(i);
                    Instant orderTs = Instant.parse(orderNode.get("timestamp").asText());
                    if (!orderTs.isBefore(reportInstant) && orderTs.isBefore(reportInstant.plusSeconds(3600))) {
                        relevantOrders.add(orderNode);
                    }
                }

                // Calculate totals by pair for EXECUTED orders
                Map<String, BigDecimal> totalsByPair = relevantOrders.stream()
                        .filter(o -> "EXECUTED".equals(o.get("status").asText()))
                        .collect(Collectors.groupingBy(
                                o -> o.get("pair").asText(),
                                Collectors.mapping(
                                        o -> new BigDecimal(o.get("amount").asText()),
                                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                                )
                        ));

                // Update reportEntity totalsByPair field
                ObjectNode totalsNode = mapper.createObjectNode();
                totalsByPair.forEach((pair, total) -> totalsNode.put(pair, total.toString()));
                reportEntity.set("totalsByPair", totalsNode);

                // Send report email asynchronously - fire and forget
                sendReportEmailAsync(reportTimestamp, totalsByPair);

                log.info("HourlyReport workflow done for hour: {}", reportTimestamp);

                return reportEntity;
            } catch (Exception e) {
                log.error("Error in HourlyReport workflow", e);
                return reportEntity;
            }
        });
    }

    /**
     * Fire and forget email sending method.
     * Called inside workflow without blocking the persistence.
     */
    private void sendReportEmailAsync(String reportTimestamp, Map<String, BigDecimal> totalsByPair) {
        CompletableFuture.runAsync(() -> {
            try {
                // Simulate email sending delay
                Thread.sleep(1000);
                log.info("Email sent for report hour {} with data: {}", reportTimestamp, totalsByPair);
            } catch (InterruptedException e) {
                log.error("Email sending interrupted", e);
                Thread.currentThread().interrupt();
            }
        });
    }
}