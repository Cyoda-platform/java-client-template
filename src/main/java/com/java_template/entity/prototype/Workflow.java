package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("prototype")
@RequiredArgsConstructor
public class Workflow {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";
    private static final String ENTITY_NAME = "prototype";

    public CompletableFuture<ObjectNode> isDefaultAction(ObjectNode entity) {
        boolean value = !entity.has("action") || entity.get("action").asText().isEmpty() ||
                (!"fetchScores".equals(entity.get("action").asText()) && !"subscribe".equals(entity.get("action").asText()));
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isFetchScoresAction(ObjectNode entity) {
        boolean value = entity.has("action") && "fetchScores".equals(entity.get("action").asText());
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isSubscribeAction(ObjectNode entity) {
        boolean value = entity.has("action") && "subscribe".equals(entity.get("action").asText());
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processFetchScores(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!entity.hasNonNull("date")) {
                    log.warn("processFetchScores: missing date field");
                    entity.put("gamesCount", 0);
                    entity.put("error", "Missing date");
                    return entity;
                }

                String date = entity.get("date").asText();
                log.info("processprototype: fetching scores for date={}", date);

                String url = String.format(EXTERNAL_API_TEMPLATE, date, API_KEY);
                URI uri = new URI(url);
                String rawJson = entityService.getRestTemplate().getForObject(uri, String.class);
                if (rawJson == null || rawJson.isEmpty()) {
                    log.warn("Empty response from external API for date {}", date);
                    entity.put("gamesCount", 0);
                    entity.put("error", "Empty response from external API");
                    return entity;
                }

                JsonNode rootNode = objectMapper.readTree(rawJson);
                List<ObjectNode> games = new ArrayList<>();

                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        ObjectNode gameNode = parseGameToObjectNode(node);
                        games.add(gameNode);
                    }
                } else {
                    log.warn("Unexpected JSON structure from external API for date {}", date);
                }

                // Add each game as a separate entity of different model ("game"), avoiding recursion
                for (ObjectNode gameNode : games) {
                    entityService.addItem(
                            "game",
                            ENTITY_VERSION,
                            gameNode,
                            o -> CompletableFuture.completedFuture(o) // trivial workflow for games
                    );
                }

                entity.put("gamesCount", games.size());

                // Send notifications asynchronously as supplementary entities or logs
                List<String> subscriberEmails = getSubscriberEmailsSync();

                StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
                if (games.isEmpty()) {
                    summary.append("No games played on this day.");
                } else {
                    for (ObjectNode g : games) {
                        String homeTeam = g.path("homeTeam").asText();
                        String awayTeam = g.path("awayTeam").asText();
                        int homeScore = g.path("homeScore").asInt(-1);
                        int awayScore = g.path("awayScore").asInt(-1);
                        summary.append(String.format("%s %d - %d %s\n", homeTeam, homeScore, awayScore, awayTeam));
                    }
                }

                for (String email : subscriberEmails) {
                    ObjectNode notification = objectMapper.createObjectNode();
                    notification.put("email", email);
                    notification.put("subject", "NBA Scores Notification");
                    notification.put("body", summary.toString());
                    notification.put("date", date);

                    entityService.addItem(
                            "notification",
                            ENTITY_VERSION,
                            notification,
                            o -> CompletableFuture.completedFuture(o) // trivial workflow
                    );
                    log.info("Queued notification for email {}", email);
                }

                return entity;
            } catch (Exception e) {
                log.error("Error in processFetchScores workflow", e);
                entity.put("error", "Failed to fetch or process scores");
                return entity;
            }
        });
    }

    public CompletableFuture<ObjectNode> processSubscribe(ObjectNode entity) {
        // No processing logic needed, just return entity immediately
        return CompletableFuture.completedFuture(entity);
    }

    private ObjectNode parseGameToObjectNode(JsonNode node) {
        ObjectNode gameNode = objectMapper.createObjectNode();
        gameNode.put("date", node.path("Day").asText(""));
        gameNode.put("homeTeam", node.path("HomeTeam").asText(""));
        gameNode.put("awayTeam", node.path("AwayTeam").asText(""));
        gameNode.put("homeScore", node.path("HomeTeamScore").asInt(-1));
        gameNode.put("awayScore", node.path("AwayTeamScore").asInt(-1));
        return gameNode;
    }

    private List<String> getSubscriberEmailsSync() throws ExecutionException, InterruptedException {
        SearchConditionRequest cond = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EXISTS", null));
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> future = entityService.getItemsByCondition(
                ENTITY_NAME, ENTITY_VERSION, cond);
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = future.get();
        List<String> emails = new ArrayList<>();
        arrayNode.forEach(jsonNode -> {
            if (jsonNode.has("email")) {
                emails.add(jsonNode.get("email").asText());
            }
        });
        return emails;
    }
}