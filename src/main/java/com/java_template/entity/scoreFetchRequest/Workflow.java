package com.java_template.entity.scoreFetchRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.entityService.EntityService;
import com.java_template.common.entityService.SearchConditionRequest;
import com.java_template.common.entityService.Condition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component("scoreFetchRequest")
@Slf4j
@RequiredArgsConstructor
public class Workflow {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure key
    private static final String EXTERNAL_API_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    // Workflow orchestration entry point
    public CompletableFuture<ObjectNode> processScoreFetchRequest(ObjectNode entity) {
        return fetchExternalScores(entity)
                .thenCompose(root -> deleteOldGames(entity, root))
                .thenCompose(root -> addNewGames(entity, root))
                .thenCompose(root -> notifySubscribers(entity, root))
                .exceptionally(ex -> {
                    log.error("Error in processScoreFetchRequest workflow", ex);
                    return entity;
                });
    }

    // Fetch scores from external API, return JSON array root node wrapped in CompletableFuture
    private CompletableFuture<JsonNode> fetchExternalScores(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateStr = entity.get("date").asText();
                String url = String.format(EXTERNAL_API_TEMPLATE, dateStr, EXTERNAL_API_KEY);
                String jsonResponse = entityService.getRestTemplate().getForObject(new URI(url), String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                if (!root.isArray()) {
                    log.error("Unexpected response format during score fetch");
                    return null;
                }
                // Update entity state if needed
                entity.put("fetchStatus", "fetched");
                return root;
            } catch (Exception e) {
                log.error("Failed to fetch external scores", e);
                return null;
            }
        });
    }

    // Delete old games for the date in entity, then pass root forward
    private CompletableFuture<JsonNode> deleteOldGames(ObjectNode entity, JsonNode root) {
        if (root == null) return CompletableFuture.completedFuture(entity);
        String dateStr = entity.get("date").asText();
        Condition condition = Condition.of("$.date", "EQUALS", dateStr);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition("game", ENTITY_VERSION, searchCondition)
                .thenCompose(oldGames -> {
                    List<CompletableFuture<Void>> deletes = oldGames.stream()
                            .map(oldGame -> {
                                UUID techId = UUID.fromString(oldGame.get("technicalId").asText());
                                return entityService.deleteItem("game", ENTITY_VERSION, techId).thenAccept(v -> {});
                            })
                            .collect(Collectors.toList());
                    return CompletableFuture.allOf(deletes.toArray(new CompletableFuture[0]))
                            .thenApply(v -> root);
                });
    }

    // Add new games from root JSON array, then pass root forward
    private CompletableFuture<JsonNode> addNewGames(ObjectNode entity, JsonNode root) {
        if (root == null) return CompletableFuture.completedFuture(entity);
        String dateStr = entity.get("date").asText();

        List<CompletableFuture<Void>> addFutures = new ArrayList<>();
        for (JsonNode gameNode : root) {
            Map<String, Object> gameMap = new HashMap<>();
            gameMap.put("gameId", gameNode.path("GameID").asText());
            gameMap.put("date", dateStr);
            gameMap.put("homeTeam", gameNode.path("HomeTeam").asText());
            gameMap.put("awayTeam", gameNode.path("AwayTeam").asText());
            if (gameNode.hasNonNull("HomeTeamScore")) {
                gameMap.put("homeScore", gameNode.get("HomeTeamScore").asInt());
            }
            if (gameNode.hasNonNull("AwayTeamScore")) {
                gameMap.put("awayScore", gameNode.get("AwayTeamScore").asInt());
            }
            // processGame is a workflow to be applied on the game entity, assumed injected or accessible
            CompletableFuture<UUID> addFuture = entityService.addItem("game", ENTITY_VERSION, gameMap, entityService.getWorkflow("game/processGame"));
            addFutures.add(addFuture.thenAccept(uuid -> {}));
        }
        return CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> root);
    }

    // Notify subscribers asynchronously, then complete with entity
    private CompletableFuture<ObjectNode> notifySubscribers(ObjectNode entity, JsonNode root) {
        if (root == null) return CompletableFuture.completedFuture(entity);

        return entityService.getItems("subscriber", ENTITY_VERSION)
                .thenApply(subs -> {
                    if (subs.isEmpty()) return entity;

                    String dateStr = entity.get("date").asText();
                    StringBuilder content = new StringBuilder("Daily NBA Scores for ").append(dateStr).append(":\n\n");
                    for (JsonNode g : root) {
                        content.append(g.path("AwayTeam").asText())
                                .append(" @ ").append(g.path("HomeTeam").asText())
                                .append(": ").append(g.path("AwayTeamScore").isInt() ? g.path("AwayTeamScore").asInt() : "N/A")
                                .append(" - ").append(g.path("HomeTeamScore").isInt() ? g.path("HomeTeamScore").asInt() : "N/A")
                                .append("\n");
                    }
                    for (JsonNode sub : subs) {
                        String email = sub.get("email").asText();
                        log.info("Sending email to {}:\n{}", email, content);
                        // TODO: Replace with real email sending logic
                    }
                    entity.put("notificationStatus", "sent");
                    return entity;
                });
    }
}