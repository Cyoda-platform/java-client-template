package com.java_template.entity.Order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class OrderWorkflow {

    private final EntityService entityService;
    private final ObjectMapper mapper;

    private static final String ENTITY_NAME = "Order";
    private static final String HOURLY_REPORT_ENTITY = "HourlyReport";

    public OrderWorkflow(EntityService entityService, ObjectMapper mapper) {
        this.entityService = entityService;
        this.mapper = mapper;
    }

    public CompletableFuture<ObjectNode> processOrder(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                entity.put("processedTimestamp", Instant.now().toString());

                if ("REJECTED".equals(entity.get("status").asText())) {
                    entity.put("rejectionReason", "Simulated rejection");
                }

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

    public CompletableFuture<ObjectNode> processNotification(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processHourlyReport(ObjectNode reportEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String reportTimestamp = reportEntity.get("reportTimestamp").asText();
                log.info("Processing HourlyReport workflow for hour: {}", reportTimestamp);

                Instant reportInstant = Instant.parse(reportTimestamp);

                ArrayNode ordersArray;
                try {
                    ordersArray = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
                } catch (Exception e) {
                    log.error("Failed to fetch orders for report generation", e);
                    return reportEntity;
                }

                List<ObjectNode> relevantOrders = new ArrayList<>();
                for (int i = 0; i < ordersArray.size(); i++) {
                    ObjectNode orderNode = (ObjectNode) ordersArray.get(i);
                    Instant orderTs = Instant.parse(orderNode.get("timestamp").asText());
                    if (!orderTs.isBefore(reportInstant) && orderTs.isBefore(reportInstant.plusSeconds(3600))) {
                        relevantOrders.add(orderNode);
                    }
                }

                Map<String, BigDecimal> totalsByPair = relevantOrders.stream()
                        .filter(o -> "EXECUTED".equals(o.get("status").asText()))
                        .collect(Collectors.groupingBy(
                                o -> o.get("pair").asText(),
                                Collectors.mapping(
                                        o -> new BigDecimal(o.get("amount").asText()),
                                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                                )
                        ));

                ObjectNode totalsNode = mapper.createObjectNode();
                totalsByPair.forEach((pair, total) -> totalsNode.put(pair, total.toString()));
                reportEntity.set("totalsByPair", totalsNode);

                sendReportEmailAsync(reportTimestamp, totalsByPair);

                log.info("HourlyReport workflow done for hour: {}", reportTimestamp);

                return reportEntity;
            } catch (Exception e) {
                log.error("Error in HourlyReport workflow", e);
                return reportEntity;
            }
        });
    }

    private void sendReportEmailAsync(String reportTimestamp, Map<String, BigDecimal> totalsByPair) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                log.info("Email sent for report hour {} with data: {}", reportTimestamp, totalsByPair);
            } catch (InterruptedException e) {
                log.error("Email sending interrupted", e);
                Thread.currentThread().interrupt();
            }
        });
    }
}