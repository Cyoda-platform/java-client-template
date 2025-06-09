package com.java_template.entity.Entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@Slf4j
public class EntityWorkflow {

    private final ObjectMapper objectMapper;

    public EntityWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        // Workflow orchestration only
        return processFetchData(entity);
    }

    public CompletableFuture<ObjectNode> processFetchData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = entity.get("apiUrl").asText();
                String response = new org.springframework.web.client.RestTemplate().getForObject(apiUrl, String.class);
                JsonNode fetchedData = objectMapper.readTree(response);
                entity.set("fetchedData", fetchedData);
                entity.put("fetchedAt", LocalDateTime.now().toString());
                entity.remove("fetchError"); // clear any previous error
                log.info("Data fetched successfully for entity: {}", entity);
            } catch (Exception e) {
                log.error("Failed to fetch data for entity. Error: {}", e.getMessage());
                entity.put("fetchError", "Failed to fetch data");
            }
            return entity;
        });
    }
}