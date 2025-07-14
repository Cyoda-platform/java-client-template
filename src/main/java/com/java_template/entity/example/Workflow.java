package com.java_template.entity.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component("example")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Condition function: check if external data should be fetched based on criteria
    public CompletableFuture<JsonNode> shouldFetchExternalData(JsonNode entity) {
        boolean result = false;
        if (entity.has("type") && !entity.get("type").asText().isBlank()) {
            result = true;
        } else if (entity.has("status") && !entity.get("status").asText().isBlank()) {
            result = true;
        }
        entity = entity.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) entity).put("success", result);
        logger.info("Condition shouldFetchExternalData evaluated to {}", result);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: check if local data processing should be done
    public CompletableFuture<JsonNode> shouldProcessLocalData(JsonNode entity) {
        // For prototype, assume always true if not fetching external data
        boolean result = true;
        entity = entity.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) entity).put("success", result);
        logger.info("Condition shouldProcessLocalData evaluated to {}", result);
        return CompletableFuture.completedFuture(entity);
    }

    // Processing function: fetch external pets from Petstore API asynchronously
    public CompletableFuture<JsonNode> fetchExternalPetsAsync(JsonNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String statusParam = "available";
                if (entity.has("status") && !entity.get("status").asText().isBlank()) {
                    statusParam = entity.get("status").asText();
                }
                String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;
                logger.info("Fetching external pets from Petstore API: {}", url);
                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                // put externalPets into entity for further processing
                ((com.fasterxml.jackson.databind.node.ObjectNode) entity).set("externalPets", root);
                return entity;
            } catch (Exception e) {
                logger.error("Error fetching external pets", e);
                return entity;
            }
        });
    }

    // Processing function: process local pet data (add/update/adopt)
    public CompletableFuture<JsonNode> processLocalPet(JsonNode entity) {
        // Simulate processing local pet data
        if (!entity.has("id") || entity.get("id").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) entity).put("id", UUID.randomUUID().toString());
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) entity).put("processed", true);
        logger.info("Processed local pet with id={}", entity.get("id").asText());
        return CompletableFuture.completedFuture(entity);
    }

    // Sample processing function from initial code
    public CompletableFuture<JsonNode> processExample(JsonNode data) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("processed", true);
        logger.info("processExample called, marked processed=true");
        return CompletableFuture.completedFuture(data);
    }

    // Condition function: check if pet can be adopted (status == available)
    public CompletableFuture<JsonNode> canAdopt(JsonNode entity) {
        boolean result = false;
        if (entity.has("status") && "available".equalsIgnoreCase(entity.get("status").asText())) {
            result = true;
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) entity).put("success", result);
        logger.info("Condition canAdopt evaluated to {}", result);
        return CompletableFuture.completedFuture(entity);
    }
}