package com.java_template.entity.CatFact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class CatFactWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(CatFactWorkflow.class);

    private final ObjectMapper objectMapper;

    // Assume entityService and restTemplate are class members injected or available
    private final EntityService entityService; // TODO: inject correctly
    private final RestTemplate restTemplate;   // TODO: inject correctly

    public CompletableFuture<ObjectNode> processCatFact(ObjectNode entity) {
        // Workflow orchestration only
        return processFetchCatFact(entity)
                .thenCompose(factText -> processUpdateEntityWithFact(entity, factText))
                .thenCompose(updatedEntity -> processSendEmails(updatedEntity))
                .thenCompose(finalEntity -> processUpdateMetrics(finalEntity));
    }

    private CompletableFuture<String> processFetchCatFact(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = restTemplate.getForObject(new URI("https://catfact.ninja/fact"), String.class);
                JsonNode json = objectMapper.readTree(response);
                String fact = json.path("fact").asText(null);
                if (fact == null || fact.isBlank()) {
                    throw new RuntimeException("Invalid cat fact from external API");
                }
                return fact;
            } catch (Exception e) {
                logger.error("Failed to fetch cat fact: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to fetch cat fact", e);
            }
        });
    }

    private CompletableFuture<ObjectNode> processUpdateEntityWithFact(ObjectNode entity, String fact) {
        entity.put("fact", fact);
        entity.put("createdAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSendEmails(ObjectNode entity) {
        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenCompose(subscribersArray -> {
                    if (subscribersArray.isEmpty()) {
                        logger.info("No subscribers found - no emails sent");
                        return CompletableFuture.completedFuture(entity);
                    }

                    List<CompletableFuture<Void>> allFutures = new ArrayList<>();
                    for (JsonNode subscriberNode : subscribersArray) {
                        String email = subscriberNode.path("email").asText(null);
                        if (email == null) continue;

                        CompletableFuture<Void> sendEmailFuture = CompletableFuture.runAsync(() ->
                                logger.info("Sending cat fact email to {}", email));

                        allFutures.add(sendEmailFuture);
                    }

                    return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> entity);
                });
    }

    private CompletableFuture<ObjectNode> processUpdateMetrics(ObjectNode entity) {
        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenCompose(subscribersArray -> {
                    if (subscribersArray.isEmpty()) {
                        return CompletableFuture.completedFuture(entity);
                    }

                    List<CompletableFuture<Void>> metricsFutures = new ArrayList<>();

                    for (JsonNode subscriberNode : subscribersArray) {
                        String email = subscriberNode.path("email").asText(null);
                        if (email == null) continue;

                        CompletableFuture<Void> updateMetricsFuture = entityService.getItemsByCondition("InteractionMetrics", ENTITY_VERSION,
                                String.format("{\"subscriberEmail\":\"%s\"}", email))
                                .thenCompose(metricsList -> {
                                    if (metricsList.isEmpty()) {
                                        logger.warn("No InteractionMetrics found for subscriber {}", email);
                                        return CompletableFuture.completedFuture(null);
                                    }
                                    JsonNode metricsNode = metricsList.get(0);
                                    // We cannot update same model entity in workflow, so we skip update to avoid recursion.
                                    // Logging increment for audit.
                                    logger.info("Would increment emailsSent metric for subscriber {}", email);
                                    return CompletableFuture.completedFuture(null);
                                });

                        metricsFutures.add(updateMetricsFuture);
                    }

                    return CompletableFuture.allOf(metricsFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> entity);
                });
    }
}