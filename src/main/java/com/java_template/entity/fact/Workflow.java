package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;

public class Workflow {

    // Assume logger, restTemplate, objectMapper, subscribers, factOpenTracking, lastFactId, sendEmail exist in the class

    public CompletableFuture<ObjectNode> processFact(ObjectNode entity) {
        // Workflow orchestration only
        return processEnsureFactId(entity)
            .thenCompose(this::processEnsureFactText)
            .thenCompose(this::processInitializeTracking)
            .thenCompose(this::processSendEmails)
            .exceptionally(ex -> {
                // Log error in orchestration
                // logger.error("Error in fact workflow orchestration", ex);
                return entity;
            });
    }

    private CompletableFuture<ObjectNode> processEnsureFactId(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String factId = entity.path("factId").asText(null);
            if (factId == null || factId.isEmpty()) {
                factId = UUID.randomUUID().toString();
                entity.put("factId", factId);
            }
            // Set lastFactId directly on class field
            lastFactId = factId;
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processEnsureFactText(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String factText = entity.path("fact").asText(null);
            if (factText == null || factText.isEmpty()) {
                try {
                    String catFactApiUrl = "https://catfact.ninja/fact";
                    String json = restTemplate.getForObject(catFactApiUrl, String.class);
                    JsonNode root = objectMapper.readTree(json);
                    factText = root.path("fact").asText(null);
                    if (factText == null || factText.isEmpty()) {
                        // logger.error("Cat fact API returned no fact in workflow");
                        throw new RuntimeException("Failed to retrieve cat fact");
                    }
                    entity.put("fact", factText);
                } catch (Exception e) {
                    // logger.error("Failed to fetch cat fact in workflow", e);
                    // Persist entity even if fact fetch failed
                    return entity;
                }
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processInitializeTracking(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String factId = entity.path("factId").asText(null);
            if (factId != null) {
                factOpenTracking.putIfAbsent(factId, new ConcurrentSkipListSet<>());
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSendEmails(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String factId = entity.path("factId").asText(null);
            String factText = entity.path("fact").asText(null);
            if (factId != null && factText != null) {
                CompletableFuture.runAsync(() -> {
                    Set<Map.Entry<String, Subscriber>> entries = subscribers.entrySet();
                    for (Map.Entry<String, Subscriber> entry : entries) {
                        Subscriber sub = entry.getValue();
                        if (sub.isActive()) {
                            try {
                                sendEmail(sub.getEmail(), factId, factText);
                            } catch (Exception e) {
                                // logger.error("Failed to send email to {}", sub.getEmail(), e);
                            }
                        }
                    }
                }); // fire-and-forget
            }
            return entity;
        });
    }
}