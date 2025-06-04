package com.java_template.entity.ReportRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ReportRequestWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(ReportRequestWorkflow.class);

    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public ReportRequestWorkflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processReportRequest(ObjectNode entity) {
        try {
            String toEmail = entity.get("email").asText();
            return entityService.getItems("Order", ENTITY_VERSION)
                    .thenCompose(this::processAggregateOrders)
                    .thenCompose(content -> processSendEmail(toEmail, "Hourly Report", content))
                    .thenApply(v -> {
                        // Modify entity state here if needed
                        entity.put("lastReportSentAt", System.currentTimeMillis());
                        return entity;
                    })
                    .exceptionally(ex -> {
                        logger.error("Failed during processReportRequest workflow", ex);
                        return entity; // proceed without failing persistence
                    });
        } catch (Exception e) {
            logger.error("Exception in processReportRequest workflow function", e);
            return CompletableFuture.completedFuture(entity);
        }
    }

    private CompletableFuture<String> processAggregateOrders(ObjectNode ordersNode) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, SummaryAccumulator> summaryMap = new HashMap<>();
            for (String pair : PAIRS) {
                summaryMap.put(pair, new SummaryAccumulator());
            }
            for (int i = 0; i < ordersNode.size(); i++) {
                ObjectNode order = (ObjectNode) ordersNode.get(i);
                String pair = order.get("pair").asText();
                SummaryAccumulator acc = summaryMap.get(pair);
                if (acc != null) {
                    acc.count++;
                    acc.totalAmount += order.get("amount").asDouble();
                    acc.totalPrice += order.get("price").asDouble();
                }
            }
            StringBuilder content = new StringBuilder("Hourly Orders Summary:\n");
            for (String pair : PAIRS) {
                SummaryAccumulator acc = summaryMap.get(pair);
                double avgPrice = acc.count > 0 ? acc.totalPrice / acc.count : 0.0;
                content.append(String.format("- %s: total=%.4f, avg=%.2f%n", pair, acc.totalAmount, avgPrice));
            }
            return content.toString();
        });
    }

    private CompletableFuture<Void> processSendEmail(String toEmail, String subject, String body) {
        // TODO: Replace with real async email sending
        return CompletableFuture.runAsync(() -> {
            logger.info("Sending email to {} with subject '{}':\n{}", toEmail, subject, body);
        });
    }

    private static class SummaryAccumulator {
        int count = 0;
        double totalAmount = 0.0;
        double totalPrice = 0.0;
    }
}