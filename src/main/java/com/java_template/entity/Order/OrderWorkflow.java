package com.java_template.entity.Order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class OrderWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(OrderWorkflow.class);

    private final ObjectMapper objectMapper;

    private final EntityService entityService;

    public OrderWorkflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    // Orchestrates the workflow steps without business logic
    public CompletableFuture<ObjectNode> processOrder(ObjectNode orderNode) {
        return processAdjustPrice(orderNode)
                .thenCompose(this::processAddConfigFlag)
                .exceptionally(ex -> {
                    logger.error("Exception in processOrder workflow", ex);
                    return orderNode;
                });
    }

    // Adjusts the price by multiplying with a factor
    private CompletableFuture<ObjectNode> processAdjustPrice(ObjectNode orderNode) {
        try {
            if (orderNode.hasNonNull("price")) {
                double price = orderNode.get("price").asDouble();
                double adjustedPrice = price * 1.001;
                orderNode.put("price", adjustedPrice);
            }
        } catch (Exception e) {
            logger.error("Exception in processAdjustPrice", e);
        }
        return CompletableFuture.completedFuture(orderNode);
    }

    // Adds a flag 'hasConfig' based on presence of Config entities
    private CompletableFuture<ObjectNode> processAddConfigFlag(ObjectNode orderNode) {
        return entityService.getItems("Config", ENTITY_VERSION)
                .thenApply(configsNode -> {
                    boolean hasConfig = configsNode != null && configsNode.size() > 0;
                    orderNode.put("hasConfig", hasConfig);
                    return orderNode;
                })
                .exceptionally(ex -> {
                    logger.warn("Failed to fetch Config entities during processAddConfigFlag", ex);
                    orderNode.put("hasConfig", false);
                    return orderNode;
                });
    }
}