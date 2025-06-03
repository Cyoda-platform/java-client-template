package com.java_template.entity.CatFact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class CatFactWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(CatFactWorkflow.class);

    private final ObjectMapper objectMapper;

    // TODO: Inject entityService and factsSentCounter via constructor or setter
    private EntityService entityService; // placeholder, replace with actual injection
    private Counter factsSentCounter; // placeholder, replace with actual injection

    public CatFactWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Workflow orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processCatFact(ObjectNode entity) {
        return processSetTimestamp(entity)
                .thenCompose(this::processFetchSubscribers)
                .thenCompose(this::processSendEmails)
                .exceptionally(ex -> {
                    logger.error("Error in processCatFact workflow", ex);
                    return entity;
                });
    }

    // Process: set timestamp if missing
    public CompletableFuture<ObjectNode> processSetTimestamp(ObjectNode entity) {
        if (!entity.hasNonNull("timestamp")) {
            entity.put("timestamp", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Process: fetch subscribers from entityService
    public CompletableFuture<ObjectNode> processFetchSubscribers(ObjectNode entity) {
        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenApply(subscribersArray -> {
                    List<ObjectNode> subscribers = new ArrayList<>();
                    for (JsonNode node : subscribersArray) {
                        if (node.isObject()) {
                            subscribers.add((ObjectNode) node);
                        }
                    }
                    // Store subscribers in entity for next step
                    entity.set("subscribers", objectMapper.valueToTree(subscribers));
                    return entity;
                });
    }

    // Process: send emails to subscribers and increment factsSentCounter
    public CompletableFuture<ObjectNode> processSendEmails(ObjectNode entity) {
        List<ObjectNode> subscribers = new ArrayList<>();
        if (entity.has("subscribers") && entity.get("subscribers").isArray()) {
            for (JsonNode node : entity.withArray("subscribers")) {
                if (node.isObject()) {
                    subscribers.add((ObjectNode) node);
                }
            }
        }
        // Remove subscribers from entity after read to avoid persisting unnecessary data
        entity.remove("subscribers");

        return sendEmailsAsync(entity, subscribers)
                .thenApply(v -> {
                    factsSentCounter.incrementAndGet();
                    return entity;
                });
    }

    // Mock or placeholder for sending emails asynchronously
    private CompletableFuture<Void> sendEmailsAsync(ObjectNode catFact, List<ObjectNode> subscribers) {
        // TODO: Implement real async email sending logic
        return CompletableFuture.runAsync(() -> {
            // simulate sending emails
            for (ObjectNode sub : subscribers) {
                logger.info("Sending cat fact email to subscriber: {}", sub.path("email").asText());
            }
        });
    }
}