package com.java_template.entity.Subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberWorkflow.class);
    private final ObjectMapper objectMapper;

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        logger.info("Starting workflow orchestration for subscriber: {}", entity.get("email").asText());

        return processSetInitialStatus(entity)
                .thenCompose(this::processFetchCatFact)
                .thenCompose(this::processFinalize);
    }

    private CompletableFuture<ObjectNode> processSetInitialStatus(ObjectNode entity) {
        logger.info("Setting initial status for subscriber: {}", entity.get("email").asText());
        entity.put("status", "pending_verification");
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processFetchCatFact(ObjectNode entity) {
        logger.info("Fetching cat fact for subscriber: {}", entity.get("email").asText());

        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = "https://catfact.ninja/fact";
                String response = new org.springframework.web.client.RestTemplate().getForObject(apiUrl, String.class);
                JsonNode jsonNode = objectMapper.readTree(response);
                String fact = jsonNode.get("fact").asText();
                entity.put("catFact", fact);
                logger.info("Added cat fact to subscriber: {}", fact);
            } catch (Exception e) {
                logger.error("Error fetching cat fact: {}", e.getMessage());
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFinalize(ObjectNode entity) {
        logger.info("Finalizing subscriber processing: {}", entity.get("email").asText());
        // Additional processing or cleanup can be added here if needed
        return CompletableFuture.completedFuture(entity);
    }
}