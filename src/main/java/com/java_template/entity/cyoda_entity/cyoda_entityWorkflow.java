package com.java_template.entity.cyoda_entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class cyoda_entityWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(cyoda_entityWorkflow.class);

    private final ObjectMapper objectMapper;

    public cyoda_entityWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processCyoda_entity(ObjectNode entity) {
        // Workflow orchestration only, no business logic here
        return processValidateApiUrl(entity)
                .thenCompose(validEntity -> {
                    if (validEntity == null) return CompletableFuture.completedFuture(entity);
                    return processFetchExternalData(validEntity);
                });
    }

    private CompletableFuture<ObjectNode> processValidateApiUrl(ObjectNode entity) {
        JsonNode apiUrlNode = entity.get("api_url");
        if (apiUrlNode == null || !apiUrlNode.isTextual()) {
            // Missing or invalid api_url: clear fetched data and fetched_at
            entity.remove("fetched_data");
            entity.remove("fetched_at");
            return CompletableFuture.completedFuture(null);
        }
        String url = apiUrlNode.asText();
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            logger.warn("Invalid api_url in workflow: {}", url);
            entity.remove("fetched_data");
            entity.remove("fetched_at");
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processFetchExternalData(ObjectNode entity) {
        String url = entity.get("api_url").asText();
        return CompletableFuture.supplyAsync(() -> {
            try {
                // TODO: consider injecting RestTemplate or HttpClient if needed
                String response = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
                if (response == null) {
                    logger.warn("Empty response from external API in workflow for url: {}", url);
                    entity.remove("fetched_data");
                    entity.remove("fetched_at");
                    return entity;
                }
                JsonNode fetchedData = objectMapper.readTree(response);
                entity.set("fetched_data", fetchedData);
                entity.put("fetched_at", Instant.now().toString());
            } catch (Exception ex) {
                logger.error("Failed to fetch or parse external API URL {} in workflow: {}", url, ex.getMessage());
                entity.remove("fetched_data");
                entity.remove("fetched_at");
            }
            return entity;
        });
    }
}