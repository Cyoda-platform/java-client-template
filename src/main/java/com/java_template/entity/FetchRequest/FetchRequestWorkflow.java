package com.java_template.entity.FetchRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class FetchRequestWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(FetchRequestWorkflow.class);
    private static final String NBA_API_KEY = "test";
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public FetchRequestWorkflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Main workflow orchestration method
//    public CompletableFuture<ObjectNode> processFetchRequest(ObjectNode entity) {
//        // Orchestration only, no business logic here; call other process* methods
//        String dateStr = entity.path("date").asText(null);
//        if (dateStr == null) {
//            logger.warn("FetchRequest entity missing required 'date' field");
//            return CompletableFuture.completedFuture(entity);
//        }
//        entity.put("status", "processing");
//        return validateDate(entity)
//                .thenCompose(this::fetchGames)
//                .thenCompose(this::storeGames)
//                .thenCompose(this::notifySubscribers)
//                .exceptionally(ex -> {
//                    logger.error("Error in workflow processFetchRequest: {}", ex.getMessage(), ex);
//                    entity.put("status", "failed");
//                    return entity;
//                });
//    }

    // Validate date format in entity, update state
    public CompletableFuture<ObjectNode> processValidateDate(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String dateStr = entity.path("date").asText(null);
            try {
                LocalDate.parse(dateStr);
                entity.put("status", "date_valid");
            } catch (DateTimeParseException e) {
                logger.error("Invalid date format: {}", dateStr);
                entity.put("status", "invalid_date");
            }
            return entity;
        });
    }

    // Fetch games from external API, add games list to entity as "gamesToStore"
    public CompletableFuture<ObjectNode> processFetchGames(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String dateStr = entity.path("date").asText(null);
            if (dateStr == null) {
                logger.warn("No date to fetch games");
                entity.put("status", "no_date");
                return entity;
            }
            try {
                String url = String.format(NBA_API_URL_TEMPLATE, dateStr, NBA_API_KEY);
                URI uri = new URI(url);

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri).GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.error("Failed to fetch NBA scores for {}: HTTP {}", dateStr, response.statusCode());
                    entity.put("status", "fetch_failed");
                    return entity;
                }
                JsonNode root = objectMapper.readTree(response.body());
                if (!root.isArray()) {
                    logger.error("Unexpected response format from NBA API for {}", dateStr);
                    entity.put("status", "invalid_response");
                    return entity;
                }

                List<ObjectNode> gamesToStore = new ArrayList<>();
                for (JsonNode gameNode : root) {
                    ObjectNode gameObj = parseGameNode(gameNode);
                    if (gameObj != null) {
                        if (!gameObj.has("date")) {
                            gameObj.put("date", dateStr);
                        }
                        gamesToStore.add(gameObj);
                    }
                }
                entity.set("gamesToStore", objectMapper.valueToTree(gamesToStore));
                entity.put("status", "games_fetched");
                logger.info("Parsed {} games for date {}", gamesToStore.size(), dateStr);
            } catch (URISyntaxException | InterruptedException | java.io.IOException e) {
                logger.error("Exception fetching games: {}", e.getMessage(), e);
                entity.put("status", "fetch_error");
            }
            return entity;
        });
    }

    // Store games entity attribute "gamesToStore" into persistence
    public CompletableFuture<ObjectNode> processStoreGames(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode gamesNode = entity.get("gamesToStore");
            if (gamesNode == null || !gamesNode.isArray() || gamesNode.size() == 0) {
                logger.warn("No games to store");
                entity.put("status", "no_games_to_store");
                return entity;
            }
            try {
                List<ObjectNode> gamesList = new ArrayList<>();
                gamesNode.forEach(node -> {
                    if (node.isObject()) {
                        gamesList.add((ObjectNode) node);
                    }
                });
//                CompletableFuture<List<UUID>> future = entityService.addItems("Game", ENTITY_VERSION, gamesList, null);
                CompletableFuture<List<UUID>> future = entityService.addItems("Game", ENTITY_VERSION, gamesList);
                future.join();
                entity.put("status", "games_stored");
                logger.info("Stored {} games", gamesList.size());
            } catch (Exception e) {
                logger.error("Failed to store games: {}", e.getMessage(), e);
                entity.put("status", "store_failed");
            }
            return entity;
        });
    }

    // Notify subscribers about the new games
    public CompletableFuture<ObjectNode> processNotifySubscribers(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode gamesNode = entity.get("gamesToStore");
            String dateStr = entity.path("date").asText(null);
            if (gamesNode == null || !gamesNode.isArray() || gamesNode.size() == 0 || dateStr == null) {
                logger.warn("No games or date for notifications");
                entity.put("status", "no_notification");
                return entity;
            }
            List<ObjectNode> gamesList = new ArrayList<>();
            gamesNode.forEach(node -> {
                if (node.isObject()) gamesList.add((ObjectNode) node);
            });
            notifySubscribers(dateStr, gamesList);
            entity.put("status", "notified");
            return entity;
        });
    }

    // Private helper to parse a single game node into ObjectNode
    private ObjectNode parseGameNode(JsonNode n) {
        try {
            String date = n.path("Day").asText(null);
            if (date == null) {
                String dt = n.path("DateTime").asText(null);
                if (dt != null && dt.length() >= 10) date = dt.substring(0, 10);
            }
            String homeTeam = n.path("HomeTeam").asText(null);
            String awayTeam = n.path("AwayTeam").asText(null);
            Integer homeScore = n.has("HomeTeamScore") && n.get("HomeTeamScore").isInt() ? n.get("HomeTeamScore").asInt() : null;
            Integer awayScore = n.has("AwayTeamScore") && n.get("AwayTeamScore").isInt() ? n.get("AwayTeamScore").asInt() : null;
            String status = n.path("Status").asText("Unknown");

            if (date == null || homeTeam == null || awayTeam == null) {
                logger.warn("Skipping incomplete game data: {}", n.toString());
                return null;
            }

            ObjectNode game = objectMapper.createObjectNode();
            game.put("date", date);
            game.put("homeTeam", homeTeam);
            game.put("awayTeam", awayTeam);
            if (homeScore != null) game.put("homeScore", homeScore);
            if (awayScore != null) game.put("awayScore", awayScore);
            game.put("status", status);

            return game;
        } catch (Exception e) {
            logger.warn("Exception parsing game node: {}", e.getMessage());
            return null;
        }
    }

    // Private helper to notify subscribers - same as original logic, mock implementation
    private void notifySubscribers(String dateStr, List<ObjectNode> games) {
        // TODO: Replace with actual email sending logic
        logger.info("Sending notifications for date {} to subscribers. Games count: {}", dateStr, games.size());
    }
}