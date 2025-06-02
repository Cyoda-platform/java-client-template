package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Workflow {

    private final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processOrder(ObjectNode entity) {
        // Orchestration only: sequence calls of processing steps
        return processSetProcessingStatus(entity)
            .thenCompose(this::processFetchProductData)
            .thenCompose(this::processSimulateDelay)
            .thenCompose(this::processSetCompletedStatus)
            .exceptionally(ex -> {
                logger.error("Workflow failed for order: {}", entity, ex);
                entity.put("workflowStatus", "failed");
                return entity;
            });
    }

    public CompletableFuture<ObjectNode> processSetProcessingStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Workflow started for order: {}", entity);
            entity.put("workflowStatus", "processing");
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> processFetchProductData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.has("items") && entity.get("items").isArray()) {
                ArrayNode items = (ArrayNode) entity.get("items");
                for (JsonNode itemNode : items) {
                    if (itemNode.isObject() && itemNode.has("productId")) {
                        String productId = itemNode.get("productId").asText();
                        try {
                            JsonNode productData = fetchProductData(productId);
                            ((ObjectNode) itemNode).set("productData", productData);
                        } catch (Exception e) {
                            logger.warn("Failed to fetch product data for productId={} due to: {}", productId, e.toString());
                            ((ObjectNode) itemNode).put("productDataError", "Failed to fetch product data");
                        }
                    }
                }
            } else {
                logger.warn("Order entity 'items' field missing or not an array: {}", entity);
            }
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> processSimulateDelay(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted during simulate delay", e);
            }
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> processSetCompletedStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("workflowStatus", "completed");
            logger.info("Workflow completed for order: {}", entity);
            return entity;
        });
    }

    // Mock external API call for product data
    private JsonNode fetchProductData(String productId) throws Exception {
        // TODO: Replace with real external API call if available
        ObjectNode mockProductData = new com.fasterxml.jackson.databind.node.JsonNodeFactory(true).objectNode();
        mockProductData.put("id", productId);
        mockProductData.put("name", "Sample Product");
        mockProductData.put("description", "This is a mock product data");
        return mockProductData;
    }
}