package com.java_template.entity.example;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component("example")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processExample(ObjectNode entity) {
        logger.info("Processing example for entity id: {}", entity.has("id") ? entity.get("id").asText() : "unknown");
        entity.put("processed", true);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isDataValid(ObjectNode entity) {
        boolean valid = entity != null && entity.hasNonNull("requiredField"); // example validation
        logger.info("Validation result for entity id {}: {}", entity.has("id") ? entity.get("id").asText() : "unknown", valid);
        entity.put("valid", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isProcessingComplete(ObjectNode entity) {
        boolean complete = entity.has("processed") && entity.get("processed").asBoolean(false);
        logger.info("Processing complete check for entity id {}: {}", entity.has("id") ? entity.get("id").asText() : "unknown", complete);
        entity.put("processingComplete", complete);
        return CompletableFuture.completedFuture(entity);
    }
}