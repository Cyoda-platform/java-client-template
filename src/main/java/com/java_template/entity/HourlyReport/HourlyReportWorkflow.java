package com.java_template.entity.HourlyReport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class HourlyReportWorkflow {

    private final EntityService entityService;
    private final ObjectMapper mapper;

    public HourlyReportWorkflow(EntityService entityService, ObjectMapper mapper) {
        this.entityService = entityService;
        this.mapper = mapper;
    }

    // Workflow orchestration only
    public CompletableFuture<ObjectNode> processHourlyReport(ObjectNode reportEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String reportTimestamp = reportEntity.get("reportTimestamp").asText();
                log.info("Processing HourlyReport workflow for hour: {}", reportTimestamp);

                Instant reportInstant = Instant.parse(reportTimestamp);

                // Fetch all orders
                ArrayNode ordersArray;
                try {
                    ordersArray = entityService.getItems("Order", ENTITY_VERSION).get();
                } catch (Exception e) {
                    log.error("Failed to fetch orders for report generation", e);
                    return reportEntity;
                }

                // Process workflow steps
                return processCalculateTotals(reportEntity, ordersArray, reportInstant)
                        .thenCompose(this::processSendEmail)
                        .join();

            } catch (Exception e) {
                log.error("Error in HourlyReport workflow", e);
                return reportEntity;
            }
        });
    }

    // Calculate totals by pair for executed orders within the hour
    private CompletableFuture<ObjectNode> processCalculateTotals(ObjectNode reportEntity, ArrayNode ordersArray, Instant reportInstant) {
        return CompletableFuture.supplyAsync(() -> {
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

            return reportEntity;
        });
    }

    // Send email asynchronously, no entity modification needed here
    private CompletableFuture<ObjectNode> processSendEmail(ObjectNode reportEntity) {
        return CompletableFuture.supplyAsync(() -> {
            String reportTimestamp = reportEntity.get("reportTimestamp").asText();
            ObjectNode totalsNode = (ObjectNode) reportEntity.get("totalsByPair");
            Map<String, BigDecimal> totalsByPair = new HashMap<>();
            totalsNode.fieldNames().forEachRemaining(pair -> {
                totalsByPair.put(pair, new BigDecimal(totalsNode.get(pair).asText()));
            });

            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000); // simulate email sending
                    log.info("Email sent for report hour {} with data: {}", reportTimestamp, totalsByPair);
                } catch (InterruptedException e) {
                    log.error("Email sending interrupted", e);
                    Thread.currentThread().interrupt();
                }
            });

            return reportEntity;
        });
    }
}