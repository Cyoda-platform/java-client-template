package com.java_template.entity.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class EntityWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(EntityWorkflow.class);
    private final ObjectMapper objectMapper;

    public EntityWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        return processNormalizeApiUrl(entity)
                .thenCompose(this::processFetchData)
                .thenApply(e -> e);
    }

    private CompletableFuture<ObjectNode> processNormalizeApiUrl(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String apiUrl = entity.get("apiUrl").asText().toLowerCase();
            entity.put("apiUrl", apiUrl);
            logger.info("Normalized apiUrl to lowercase: {}", apiUrl);
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFetchData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // TODO: Replace with actual HTTP client call to fetch data from entity.get("apiUrl")
                JsonNode fetchedData = objectMapper.readTree("{\"example\": \"data\"}");
                entity.set("fetchedData", fetchedData);
                entity.put("fetchedAt", System.currentTimeMillis());
                logger.info("Fetched data for entity with apiUrl: {}", entity.get("apiUrl").asText());
            } catch (Exception e) {
                logger.error("Error fetching data for entity with apiUrl: {}", entity.get("apiUrl").asText(), e);
            }
            return entity;
        });
    }
}