package com.java_template.entity.subscriber;

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

@Component("subscriber")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    // Condition function: hasNoSubscribedAt
    public CompletableFuture<ObjectNode> hasNoSubscribedAt(ObjectNode entity) {
        boolean result = !entity.hasNonNull("subscribedAt") || entity.get("subscribedAt").asText().isEmpty();
        entity.put("conditionResult", result);
        logger.info("Condition hasNoSubscribedAt evaluated to {}", result);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: alwaysTrue
    public CompletableFuture<ObjectNode> alwaysTrue(ObjectNode entity) {
        entity.put("conditionResult", true);
        logger.info("Condition alwaysTrue evaluated to true");
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: alwaysFalse
    public CompletableFuture<ObjectNode> alwaysFalse(ObjectNode entity) {
        entity.put("conditionResult", false);
        logger.info("Condition alwaysFalse evaluated to false");
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: factIsPresent
    public CompletableFuture<ObjectNode> factIsPresent(ObjectNode entity) {
        boolean result = entity.hasNonNull("factText") && !entity.get("factText").asText().isEmpty();
        entity.put("conditionResult", result);
        logger.info("Condition factIsPresent evaluated to {}", result);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: emailsNotSentYet
    public CompletableFuture<ObjectNode> emailsNotSentYet(ObjectNode entity) {
        boolean result = !entity.hasNonNull("emailsSent") || !entity.get("emailsSent").asBoolean(false);
        entity.put("conditionResult", result);
        logger.info("Condition emailsNotSentYet evaluated to {}", result);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: processSubscriber
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        logger.info("processSubscriber workflow started for entity: {}", entity);
        try {
            if (!entity.hasNonNull("subscribedAt") || entity.get("subscribedAt").asText().isEmpty()) {
                entity.put("subscribedAt", Instant.now().toString());
            }
        } catch (Exception e) {
            logger.error("Error in processSubscriber workflow", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Action: fetchCatFact
    public CompletableFuture<ObjectNode> fetchCatFact(ObjectNode entity) {
        logger.info("fetchCatFact workflow started");
        return CompletableFuture.supplyAsync(() -> {
            try {
                String catFactApiUrl = "https://catfact.ninja/fact";
                String rawJson = restTemplate.getForObject(catFactApiUrl, String.class);
                if (rawJson == null || rawJson.isEmpty()) {
                    throw new RuntimeException("Empty response from catfact API");
                }
                JsonNode factJson = JsonUtils.objectMapper.readTree(rawJson);
                String factText = factJson.path("fact").asText(null);
                if (factText == null || factText.isEmpty()) {
                    throw new RuntimeException("Cat fact API returned empty fact");
                }
                entity.put("factId", UUID.randomUUID().toString());
                entity.put("factText", factText);
                entity.put("sentAt", Instant.now().toString());
                return entity;
            } catch (Exception e) {
                logger.error("Error in fetchCatFact workflow", e);
                throw new RuntimeException(e);
            }
        });
    }

    // Action: sendFactEmails
    public CompletableFuture<ObjectNode> sendFactEmails(ObjectNode entity) {
        logger.info("sendFactEmails workflow started");
        try {
            int recipientsCount = 0;
            if (entity.hasNonNull("recipientsCount")) {
                recipientsCount = entity.get("recipientsCount").asInt(0);
            }
            // Simulate async email sending
            CompletableFuture.runAsync(() -> {
                logger.info("Sending emails to {} subscribers", recipientsCount);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                }
                logger.info("Email sending completed for factId={}", entity.get("factId").asText());
            });
            entity.put("emailsSent", true);
        } catch (Exception e) {
            logger.error("Error in sendFactEmails workflow", e);
        }
        return CompletableFuture.completedFuture(entity);
    }
}