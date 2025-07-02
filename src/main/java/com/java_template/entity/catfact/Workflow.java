package com.java_template.entity.catfact;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("catfact")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Atomic integer for total emails sent - assume declared and managed elsewhere as per original context
    private final java.util.concurrent.atomic.AtomicInteger totalEmailsSent = new java.util.concurrent.atomic.AtomicInteger(0);

    // Action: fetch cat fact and update entity
    public CompletableFuture<ObjectNode> fetchCatFact(ObjectNode entity) {
        logger.info("fetchCatFact started");
        return CompletableFuture.supplyAsync(() -> {
            try {
                String catFactApiUrl = "https://catfact.ninja/fact";
                String rawJson = restTemplate.getForObject(catFactApiUrl, String.class);
                if (rawJson == null || rawJson.isEmpty()) {
                    throw new RuntimeException("Empty response from catfact API");
                }
                JsonNode factJson = objectMapper.readTree(rawJson);
                String factText = factJson.path("fact").asText(null);
                if (factText == null || factText.isEmpty()) {
                    throw new RuntimeException("Cat fact API returned empty fact");
                }

                entity.put("factId", UUID.randomUUID().toString());
                entity.put("factText", factText);
                entity.put("sentAt", Instant.now().toString());

                return entity;
            } catch (Exception e) {
                logger.error("Error in fetchCatFact", e);
                throw new RuntimeException(e);
            }
        });
    }

    // Condition: factText presence and non-empty check
    public CompletableFuture<ObjectNode> isFactValid(ObjectNode entity) {
        boolean valid = false;
        try {
            String factText = entity.path("factText").asText(null);
            valid = factText != null && !factText.isEmpty();
        } catch (Exception e) {
            logger.error("Error in isFactValid", e);
        }
        entity.put("success", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: load subscribers count and update entity
    public CompletableFuture<ObjectNode> loadSubscribersCount(ObjectNode entity) {
        logger.info("loadSubscribersCount started");
        try {
            CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION);
            ArrayNode subsArray = subsFuture.join();
            int recipientsCount = subsArray != null ? subsArray.size() : 0;
            entity.put("recipientsCount", recipientsCount);
        } catch (Exception e) {
            logger.error("Error in loadSubscribersCount", e);
            entity.put("recipientsCount", 0);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: check if recipientsCount > 0
    public CompletableFuture<ObjectNode> hasSubscribers(ObjectNode entity) {
        boolean hasSubs = false;
        try {
            hasSubs = entity.path("recipientsCount").asInt(0) > 0;
        } catch (Exception e) {
            logger.error("Error in hasSubscribers", e);
        }
        entity.put("success", hasSubs);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: send emails asynchronously and update totalEmailsSent
    public CompletableFuture<ObjectNode> sendEmailsAsync(ObjectNode entity) {
        int recipientsCount = entity.path("recipientsCount").asInt(0);
        CompletableFuture.runAsync(() -> {
            logger.info("Sending emails to {} subscribers", recipientsCount);
            try {
                Thread.sleep(1000L); // simulate sending delay
            } catch (InterruptedException ignored) {
            }
            logger.info("Email sending completed for factId={}", entity.get("factId").asText());
        });
        totalEmailsSent.addAndGet(recipientsCount);
        return CompletableFuture.completedFuture(entity);
    }

}