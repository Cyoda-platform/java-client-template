package com.java_template.entity.Entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@Slf4j
public class EntityWorkflow {

    private final ObjectMapper objectMapper;

    public EntityWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestration method only - no business logic here
    public CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        return 
            processFetchData(entity)
            .thenCompose(this::processPostFetchProcessing);
    }

    // Fetch data from API URL and update the entity fields
    public CompletableFuture<ObjectNode> processFetchData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String apiUrl = entity.get("apiUrl").asText();
            log.info("processFetchData: Fetching data from API URL: {}", apiUrl);
            try {
                String response = new org.springframework.web.client.RestTemplate().getForObject(apiUrl, String.class);
                JsonNode fetchedData = objectMapper.readTree(response);
                entity.set("fetchedData", fetchedData);
                entity.put("fetchedAt", String.valueOf(System.currentTimeMillis()));
            } catch (Exception e) {
                log.error("processFetchData: Error fetching data from URL: {}", apiUrl, e);
            }
            return entity;
        });
    }

    // Example post-fetch processing method, currently no-op but can be extended
    public CompletableFuture<ObjectNode> processPostFetchProcessing(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }
}