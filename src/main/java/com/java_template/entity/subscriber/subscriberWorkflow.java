package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberWorkflow.class);
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestrates the workflow steps, no business logic here
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        return processFetchCatFact(entity)
                .thenCompose(this::processAddCatFactToEntity)
                .thenCompose(this::processMarkEntityProcessed);
    }

    // Fetch cat fact from external API and store it temporarily in entity property "tempCatFact"
    public CompletableFuture<ObjectNode> processFetchCatFact(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(CAT_FACT_API_URL, String.class);
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                String fact = jsonResponse.get("fact").asText();
                entity.put("tempCatFact", fact);
                logger.info("Fetched cat fact: {}", fact);
            } catch (Exception e) {
                logger.error("Error retrieving cat fact", e);
                entity.put("tempCatFact", "No cat fact available");
            }
            return entity;
        });
    }

    // Move the temporary cat fact into the final "catFact" attribute and remove temporary attribute
    public CompletableFuture<ObjectNode> processAddCatFactToEntity(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String fact = entity.path("tempCatFact").asText(null);
            if (fact != null) {
                entity.put("catFact", fact);
                entity.remove("tempCatFact");
                logger.info("Added cat fact to entity");
            } else {
                logger.warn("No tempCatFact found in entity");
            }
            return entity;
        });
    }

    // Mark the entity state as processed and update version
    public CompletableFuture<ObjectNode> processMarkEntityProcessed(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("status", "processed");
            entity.put("version", ENTITY_VERSION);
            logger.info("Entity marked as processed, version set to {}", ENTITY_VERSION);
            return entity;
        });
    }
}