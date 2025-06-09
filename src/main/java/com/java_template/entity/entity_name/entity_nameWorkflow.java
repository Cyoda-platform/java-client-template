package com.java_template.entity.entity_name;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class entity_nameWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(entity_nameWorkflow.class);

    private final ObjectMapper objectMapper;

    public entity_nameWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        return processFetchData(entity)
                .thenCompose(this::processUpdateTimestamp)
                .thenApply(e -> {
                    logger.info("Finished processing entity: {}", e);
                    return e;
                });
    }

    private CompletableFuture<ObjectNode> processFetchData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = entity.get("apiUrl").asText();
                JsonNode fetchedData = fetchDataFromApi(apiUrl);
                entity.set("fetchedData", fetchedData);
            } catch (Exception e) {
                logger.error("Failed to fetch data for entity: {}", e.getMessage());
                throw new RuntimeException("Failed to fetch data", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processUpdateTimestamp(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("fetchedAt", LocalDateTime.now().toString());
            return entity;
        });
    }

    // Utility method to fetch data from API URL
    private JsonNode fetchDataFromApi(String apiUrl) throws Exception {
        // TODO: Implement actual HTTP call with retry and error handling if needed
        // Placeholder synchronous fetch (should be async in real usage)
        try {
            String response = new java.net.http.HttpClient.Builder().build()
                    .send(java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(apiUrl))
                            .GET()
                            .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString())
                    .body();
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Error fetching data from API URL {}: {}", apiUrl, e.getMessage());
            throw e;
        }
    }
}