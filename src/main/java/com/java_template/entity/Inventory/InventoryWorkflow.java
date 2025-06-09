package com.java_template.entity.Inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class InventoryWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(InventoryWorkflow.class);
    private final ObjectMapper objectMapper;

    public InventoryWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processInventory(ObjectNode entity) {
        return processMarkProcessed(entity)
                .thenCompose(this::processFetchSupplementaryData)
                .thenCompose(this::processCalculateMetrics)
                .thenCompose(this::processFinalizeReport);
    }

    // Mark entity as processed
    private CompletableFuture<ObjectNode> processMarkProcessed(ObjectNode entity) {
        entity.put("processed", true);
        return CompletableFuture.completedFuture(entity);
    }

    // Fetch supplementary data asynchronously (mock example)
    private CompletableFuture<ObjectNode> processFetchSupplementaryData(ObjectNode entity) {
        // TODO: replace with real async call if needed
        ObjectNode supplementaryData = objectMapper.createObjectNode();
        supplementaryData.put("info", "Sample supplementary data");
        entity.set("supplementaryData", supplementaryData);
        logger.info("Supplementary data added to entity");
        return CompletableFuture.completedFuture(entity);
    }

    // Calculate key metrics (totalItems, averagePrice, totalValue, etc.)
    private CompletableFuture<ObjectNode> processCalculateMetrics(ObjectNode entity) {
        // Example metrics calculation placeholder
        entity.put("totalItems", 150);
        entity.put("averagePrice", 250.75);
        entity.put("totalValue", 37500);
        logger.info("Metrics calculated and added to entity");
        return CompletableFuture.completedFuture(entity);
    }

    // Finalize report, e.g. set status or timestamp
    private CompletableFuture<ObjectNode> processFinalizeReport(ObjectNode entity) {
        entity.put("reportStatus", "ready");
        entity.put("entityVersion", ENTITY_VERSION);
        logger.info("Report finalized");
        return CompletableFuture.completedFuture(entity);
    }
}