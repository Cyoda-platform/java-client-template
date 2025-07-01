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

    public CompletableFuture<ObjectNode> fetchExternalScores(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateStr = entity.get("date").asText();
                String url = String.format(EXTERNAL_API_TEMPLATE, dateStr, EXTERNAL_API_KEY);
                String jsonResponse = entityService.getRestTemplate().getForObject(new URI(url), String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                if (!root.isArray()) {
                    log.error("Unexpected response format during score fetch");
                    entity.put("fetchStatus", "failed");
                    return entity;
                }
                entity.put("fetchStatus", "fetched");
                entity.set("fetchedScores", root);
                return entity;
            } catch (Exception e) {
                log.error("Failed to fetch external scores", e);
                entity.put("fetchStatus", "failed");
                return entity;
            }
        });
    }

    public CompletableFuture<ObjectNode> deleteOldGames(ObjectNode entity) {
        if (!entity.has("fetchStatus") || !"fetched".equals(entity.get("fetchStatus").asText())) {
            return CompletableFuture.completedFuture(entity);
        }
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
                            .thenApply(v -> {
                                entity.put("oldGamesDeleted", true);
                                return entity;
                            });
                });
    }

    public CompletableFuture<ObjectNode> addNewGames(ObjectNode entity) {
        if (!entity.has("oldGamesDeleted") || !entity.get("oldGamesDeleted").asBoolean()) {
            return CompletableFuture.completedFuture(entity);
        }
        if (!entity.has("fetchedScores")) {
            return CompletableFuture.completedFuture(entity);
        }
        JsonNode root = entity.get("fetchedScores");
        if (!root.isArray()) {
            return CompletableFuture.completedFuture(entity);
        }
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
            CompletableFuture<UUID> addFuture = entityService.addItem("game", ENTITY_VERSION, gameMap, entityService.getWorkflow("game/processGame"));
            addFutures.add(addFuture.thenAccept(uuid -> {}));
        }
        return CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    entity.put("newGamesAdded", true);
                    return entity;
                });
    }

    public CompletableFuture<ObjectNode> notifySubscribers(ObjectNode entity) {
        if (!entity.has("newGamesAdded") || !entity.get("newGamesAdded").asBoolean()) {
            return CompletableFuture.completedFuture(entity);
        }
        if (!entity.has("fetchedScores")) {
            return CompletableFuture.completedFuture(entity);
        }
        JsonNode root = entity.get("fetchedScores");
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
                    }
                    entity.put("notificationStatus", "sent");
                    return entity;
                });
    }

    public CompletableFuture<ObjectNode> completeWorkflow(ObjectNode entity) {
        if (entity.has("notificationStatus") && "sent".equals(entity.get("notificationStatus").asText())) {
            entity.put("workflowStatus", "done");
        } else {
            entity.put("workflowStatus", "incomplete");
        }
        return CompletableFuture.completedFuture(entity);
    }

}