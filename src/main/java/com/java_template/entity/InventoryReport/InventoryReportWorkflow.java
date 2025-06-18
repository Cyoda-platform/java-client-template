package com.java_template.entity.InventoryReport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class InventoryReportWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(InventoryReportWorkflow.class);

    private final ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processInventoryReport(ObjectNode entity) {
        logger.info("processInventoryReport started");
        return processFetchInventoryData(entity)
                .thenCompose(this::processCalculateReport)
                .thenApply(this::processCompleteReport);
    }

    private CompletableFuture<ObjectNode> processFetchInventoryData(ObjectNode entity) {
        // Extract filters (though unused here, kept for business logic)
        String category = entity.hasNonNull("category") ? entity.get("category").asText() : null;
        String dateFrom = entity.hasNonNull("dateFrom") ? entity.get("dateFrom").asText() : null;
        String dateTo = entity.hasNonNull("dateTo") ? entity.get("dateTo").asText() : null;

        // Set reportId and generatedAt immediately
        String reportId = UUID.randomUUID().toString();
        Instant generatedAt = Instant.now();
        entity.put("reportId", reportId);
        entity.put("generatedAt", generatedAt.toString());

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("processFetchInventoryData: calling external API for reportId={}", reportId);

                // TODO: Replace with actual external API call with filters if needed
                String response = getExternalApiResponse();

                if (response == null)
                    throw new IllegalStateException("External API returned null response");

                JsonNode inventoryData = objectMapper.readTree(response);
                if (!inventoryData.isArray())
                    throw new IllegalStateException("External API returned non-array data");

                // Store raw inventory data on the entity for next step
                entity.set("inventoryData", inventoryData);

                logger.info("processFetchInventoryData completed for reportId={}", reportId);
                return entity;
            } catch (Exception e) {
                logger.error("Error in processFetchInventoryData: {}", e.getMessage(), e);
                entity.put("error", "Failed to fetch inventory data: " + e.getMessage());
                entity.put("status", "failed");
                entity.put("errorTimestamp", Instant.now().toString());
                return entity;
            }
        });
    }

    private CompletableFuture<ObjectNode> processCalculateReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("processCalculateReport started for reportId={}", entity.get("reportId").asText());

                JsonNode inventoryData = entity.get("inventoryData");
                if (inventoryData == null || !inventoryData.isArray())
                    throw new IllegalStateException("Missing or invalid inventory data for calculation");

                int totalItems = 0;
                double totalValue = 0.0;

                for (JsonNode item : inventoryData) {
                    totalItems++;
                    double price = item.has("price") && item.get("price").isNumber() ? item.get("price").asDouble() : 0.0;
                    totalValue += price;
                }

                double averagePrice = totalItems > 0 ? totalValue / totalItems : 0.0;

                entity.put("totalItems", totalItems);
                entity.put("totalValue", totalValue);
                entity.put("averagePrice", averagePrice);

                // Remove raw inventoryData to avoid storing unnecessary data
                entity.remove("inventoryData");

                logger.info("processCalculateReport completed: totalItems={}, averagePrice={}, totalValue={}",
                        totalItems, averagePrice, totalValue);

                return entity;
            } catch (Exception e) {
                logger.error("Error in processCalculateReport: {}", e.getMessage(), e);
                entity.put("error", "Failed to calculate report: " + e.getMessage());
                entity.put("status", "failed");
                entity.put("errorTimestamp", Instant.now().toString());
                return entity;
            }
        });
    }

    private ObjectNode processCompleteReport(ObjectNode entity) {
        logger.info("processCompleteReport completed for reportId={}",
                entity.hasNonNull("reportId") ? entity.get("reportId").asText() : "unknown");
        // No additional workflow orchestration here, just returning entity
        return entity;
    }

    // TODO: Replace this mock with actual external API call as needed
    private String getExternalApiResponse() {
        // Returning null or sample JSON array string as placeholder
        return "[{\"price\":100.0},{\"price\":200.0},{\"price\":150.0}]";
    }
}