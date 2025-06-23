package com.java_template.entity.CatFact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class CatFactWorkflow {

    private final ObjectMapper objectMapper;
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    // TODO: Inject or initialize your entityService here
    private final EntityService entityService;

    public CatFactWorkflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processCatFact(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                entity = processSetCreatedAt(entity).join();
                entity = processFetchCatFact(entity).join();
                entity = processSendEmails(entity).join();
                entity = processUpdateFactInteractions(entity).join();
            } catch (Exception e) {
                log.error("Exception in processCatFact workflow orchestration", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSetCreatedAt(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.hasNonNull("createdAt")) {
                entity.put("createdAt", Instant.now().toString());
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFetchCatFact(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = entityService.getRestTemplate().getForObject(URI.create(CAT_FACT_API_URL), String.class);
                if (response != null) {
                    JsonNode apiResponse = objectMapper.readTree(response);
                    if (apiResponse.hasNonNull("fact")) {
                        String factText = apiResponse.get("fact").asText();
                        if (factText != null && !factText.isBlank()) {
                            entity.put("factText", factText);
                        } else {
                            entity.putIfAbsent("factText", "");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch cat fact from external API", e);
                entity.putIfAbsent("factText", "");
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSendEmails(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
                ArrayNode subscribers = subscribersFuture.join();

                for (JsonNode subscriber : subscribers) {
                    String email = subscriber.get("email").asText();
                    log.info("Sent cat fact email to subscriber: {}", email);
                }
                entity.put("subscribersCount", subscribers.size());
            } catch (Exception e) {
                log.error("Error sending emails to subscribers", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processUpdateFactInteractions(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID factId = null;
                if (entity.hasNonNull("factId")) {
                    try {
                        factId = UUID.fromString(entity.get("factId").asText());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid factId format in entity: {}", entity.get("factId").asText());
                    }
                }
                if (factId == null) {
                    log.warn("factId is null or invalid - skipping FactInteraction update");
                    return entity;
                }

                CompletableFuture<ArrayNode> interactionsFuture = entityService.getItemsByCondition(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, objectMapper.createObjectNode().put("factId", factId.toString()));
                ArrayNode existingInteractions = interactionsFuture.join();

                CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
                ArrayNode subscribers = subscribersFuture.join();
                int subscribersCount = subscribers.size();

                if (existingInteractions.isEmpty()) {
                    ObjectNode newInteraction = objectMapper.createObjectNode();
                    newInteraction.put("factId", factId.toString());
                    newInteraction.put("emailsSent", subscribersCount);
                    newInteraction.put("emailsOpened", 0);
                    newInteraction.put("linksClicked", 0);

                    entityService.addItem(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, newInteraction, this::processFactInteraction)
                            .exceptionally(ex -> {
                                log.error("Failed to add FactInteraction entity", ex);
                                return null;
                            });
                } else {
                    for (JsonNode interactionNode : existingInteractions) {
                        UUID interactionId;
                        try {
                            interactionId = UUID.fromString(interactionNode.get("technicalId").asText());
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid technicalId format in FactInteraction: {}", interactionNode.get("technicalId").asText());
                            continue;
                        }

                        int emailsSent = interactionNode.get("emailsSent").asInt() + subscribersCount;
                        int emailsOpened = interactionNode.get("emailsOpened").asInt();
                        int linksClicked = interactionNode.get("linksClicked").asInt();

                        ObjectNode updatedInteraction = objectMapper.createObjectNode();
                        updatedInteraction.put("factId", factId.toString());
                        updatedInteraction.put("emailsSent", emailsSent);
                        updatedInteraction.put("emailsOpened", emailsOpened);
                        updatedInteraction.put("linksClicked", linksClicked);

                        entityService.updateItem(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, interactionId, updatedInteraction)
                                .exceptionally(ex -> {
                                    log.error("Failed to update FactInteraction entity {}", interactionId, ex);
                                    return null;
                                });
                    }
                }

            } catch (Exception e) {
                log.error("Exception in processUpdateFactInteractions", e);
            }
            return entity;
        });
    }

    // Placeholder for fact interaction processing, no orchestration here
    private CompletableFuture<ObjectNode> processFactInteraction(ObjectNode entity) {
        // TODO: Implement any business logic needed for FactInteraction entity
        return CompletableFuture.completedFuture(entity);
    }
}
```