package com.java_template.entity.Notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class NotificationWorkflow {

    private final ObjectMapper mapper;

    public NotificationWorkflow(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public CompletableFuture<ObjectNode> processNotification(ObjectNode entity) {
        // No additional business logic, just return entity as is.
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processOrder(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                entity.put("processedTimestamp", Instant.now().toString());

                if ("REJECTED".equals(entity.get("status").asText())) {
                    entity.put("rejectionReason", "Simulated rejection");
                }

                if ("EXECUTED".equals(entity.get("status").asText())) {
                    // Fire-and-forget adding Notification entity must be handled outside this workflow
                    // Here we just modify this entity if needed and return
                }

                log.debug("Order processed in workflow: {}", entity.get("orderId").asText());
                return entity;
            } catch (Exception e) {
                log.error("Error processing order workflow", e);
                return entity;
            }
        });
    }

    public CompletableFuture<ObjectNode> processHourlyReport(ObjectNode reportEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String reportTimestamp = reportEntity.get("reportTimestamp").asText();
                log.info("Processing HourlyReport workflow for hour: {}", reportTimestamp);

                Instant reportInstant = Instant.parse(reportTimestamp);

                // TODO: Fetch all orders from external source (e.g. entityService) - placeholder here
                List<ObjectNode> allOrders = fetchAllOrders();

                List<ObjectNode> relevantOrders = allOrders.stream()
                        .filter(order -> {
                            Instant orderTs = Instant.parse(order.get("timestamp").asText());
                            return !orderTs.isBefore(reportInstant) && orderTs.isBefore(reportInstant.plusSeconds(3600));
                        })
                        .collect(Collectors.toList());

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

    // TODO: Replace this stub with actual fetching logic (e.g. from entityService)
    private List<ObjectNode> fetchAllOrders() {
        return List.of();
    }

    // Orchestration method, only workflow orchestration, no business logic
    public CompletableFuture<ObjectNode> processNotificationWorkflow(ObjectNode entity) {
        // Example orchestration: just call processNotification
        return processNotification(entity);
    }
}