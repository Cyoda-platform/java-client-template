package com.java_template.entity.catEvent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component("catEvent")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private EntityService entityService;

    public CompletableFuture<ObjectNode> isDramaticFoodRequest(ObjectNode entity) {
        boolean value = entity.has("eventType") && "dramatic_food_request".equalsIgnoreCase(entity.get("eventType").asText());
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isNotDramaticFoodRequest(ObjectNode entity) {
        boolean value = !entity.has("eventType") || !"dramatic_food_request".equalsIgnoreCase(entity.get("eventType").asText());
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> createNotification(ObjectNode entity) {
        if (!entity.has("message")) {
            entity.put("message", "Emergency! A cat demands snacks");
        }
        if (!entity.has("eventType")) {
            entity.put("eventType", "dramatic_food_request");
        }
        if (!entity.has("timestamp")) {
            entity.put("timestamp", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processNotification(ObjectNode entity) {
        logger.info("Processing Notification workflow for id={}", entity.has("id") ? entity.get("id").asText() : "unknown");
        return CompletableFuture.completedFuture(entity);
    }
}