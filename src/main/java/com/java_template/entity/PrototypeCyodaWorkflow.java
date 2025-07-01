package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        @NotBlank
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchScoresResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String email;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Game {
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribersResponse {
        private List<String> subscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesResponse {
        private List<Game> games;
        private Integer page;
        private Integer size;
        private Integer total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesByDateResponse {
        private String date;
        private List<Game> games;
    }

    // Workflow function for Subscriber entity: normalize email
    private Function<Object, Object> processSubscriber = entity -> {
        ObjectNode node = (ObjectNode) entity;
        if (node.has("email") && node.get("email").isTextual()) {
            String normalizedEmail = node.get("email").asText().toLowerCase(Locale.ROOT);
            node.put("email", normalizedEmail);
        }
        return node;
    };

    // Workflow function for Game entity: normalize score fields to integer or null
    private Function<Object, Object> processGame = entity -> {
        ObjectNode node = (ObjectNode) entity;
        if (!node.has("homeScore") || node.get("homeScore").isNull()) {
            node.putNull("homeScore");
        }
        if (!node.has("awayScore") || node.get("awayScore").isNull()) {
            node.putNull("awayScore");
        }
        return node;
    };

    // New entity model 'scoreFetchRequest' workflow function to fetch, delete old games, add new games, notify subscribers
    private Function<Object, Object> processScoreFetchRequest = entity -> {
        ObjectNode entityNode = (ObjectNode) entity;
        String dateStr = null;
        try {
            if (entityNode.has("date") && entityNode.get("date").isTextual()) {
                dateStr = entityNode.get("date").asText();
            } else {
                logger.error("processScoreFetchRequest: Missing or invalid date field");
                return entity;
            }

            logger.info("processScoreFetchRequest started for date: {}", dateStr);

            // Fetch external game scores data
            String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test", dateStr);
            String jsonResponse = restTemplate.getForObject(new URI(url), String.class);
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                logger.error("processScoreFetchRequest: Empty response from external API");
                return entity;
            }
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (!root.isArray()) {
                logger.error("processScoreFetchRequest: Unexpected response format, expected JSON array");
                return entity;
            }

            // Delete old games for this date
            Condition condition = Condition.of("$.date", "EQUALS", dateStr);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
            CompletableFuture<Void> deleteOldGamesFuture = entityService.getItemsByCondition("game", ENTITY_VERSION, searchCondition)
                    .thenCompose(oldGames -> {
                        List<CompletableFuture<UUID>> deletes = new ArrayList<>();
                        for (JsonNode oldGame : oldGames) {
                            if (oldGame.has("technicalId") && oldGame.get("technicalId").isTextual()) {
                                try {
                                    UUID techId = UUID.fromString(oldGame.get("technicalId").asText());
                                    deletes.add(entityService.deleteItem("game", ENTITY_VERSION, techId));
                                } catch (IllegalArgumentException e) {
                                    logger.warn("processScoreFetchRequest: Skipping invalid technicalId in old game entity");
                                }
                            }
                        }
                        return CompletableFuture.allOf(deletes.toArray(new CompletableFuture[0]));
                    });

            deleteOldGamesFuture.join();

            // Add new games with workflow processGame
            List<CompletableFuture<UUID>> addGameFutures = new ArrayList<>();
            for (JsonNode gameNode : root) {
                Map<String, Object> gameMap = new HashMap<>();
                if (gameNode.hasNonNull("GameID")) {
                    gameMap.put("gameId", gameNode.get("GameID").asText());
                }
                gameMap.put("date", dateStr);
                if (gameNode.hasNonNull("HomeTeam")) {
                    gameMap.put("homeTeam", gameNode.get("HomeTeam").asText());
                }
                if (gameNode.hasNonNull("AwayTeam")) {
                    gameMap.put("awayTeam", gameNode.get("AwayTeam").asText());
                }
                if (gameNode.hasNonNull("HomeTeamScore") && gameNode.get("HomeTeamScore").canConvertToInt()) {
                    gameMap.put("homeScore", gameNode.get("HomeTeamScore").asInt());
                } else {
                    gameMap.put("homeScore", null);
                }
                if (gameNode.hasNonNull("AwayTeamScore") && gameNode.get("AwayTeamScore").canConvertToInt()) {
                    gameMap.put("awayScore", gameNode.get("AwayTeamScore").asInt());
                } else {
                    gameMap.put("awayScore", null);
                }
                addGameFutures.add(entityService.addItem("game", ENTITY_VERSION, gameMap, processGame));
            }
            CompletableFuture.allOf(addGameFutures.toArray(new CompletableFuture[0])).join();

            // Notify subscribers asynchronously but wait for completion to avoid premature termination
            entityService.getItems("subscriber", ENTITY_VERSION).thenAccept(subs -> {
                if (subs.isEmpty()) return;
                StringBuilder content = new StringBuilder("Daily NBA Scores for ").append(dateStr).append(":\n\n");
                for (JsonNode g : root) {
                    String awayTeam = g.hasNonNull("AwayTeam") ? g.get("AwayTeam").asText() : "N/A";
                    String homeTeam = g.hasNonNull("HomeTeam") ? g.get("HomeTeam").asText() : "N/A";
                    String awayScore = (g.hasNonNull("AwayTeamScore") && g.get("AwayTeamScore").canConvertToInt()) ? String.valueOf(g.get("AwayTeamScore").asInt()) : "N/A";
                    String homeScore = (g.hasNonNull("HomeTeamScore") && g.get("HomeTeamScore").canConvertToInt()) ? String.valueOf(g.get("HomeTeamScore").asInt()) : "N/A";
                    content.append(awayTeam)
                            .append(" @ ")
                            .append(homeTeam)
                            .append(": ")
                            .append(awayScore)
                            .append(" - ")
                            .append(homeScore)
                            .append("\n");
                }
                for (JsonNode sub : subs) {
                    if (sub.hasNonNull("email")) {
                        String email = sub.get("email").asText();
                        // In real app, send email. Here just log.
                        logger.info("Sending email to {}:\n{}", email, content);
                    }
                }
            }).join();

            logger.info("processScoreFetchRequest completed successfully for date: {}", dateStr);

        } catch (Exception e) {
            logger.error("Error in processScoreFetchRequest workflow for entity: ", e);
        }
        return entity;
    };

    @PostMapping("/subscribe")
    public CompletableFuture<ResponseEntity<SubscribeResponse>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        Condition condition = Condition.of("$.email", "IEQUALS", email);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition("subscriber", ENTITY_VERSION, searchCondition)
                .thenCompose(arrayNode -> {
                    if (arrayNode.size() > 0) {
                        logger.info("Email already subscribed: {}", email);
                        return CompletableFuture.completedFuture(ResponseEntity.ok(new SubscribeResponse("Email already subscribed")));
                    } else {
                        Map<String, Object> newSub = new HashMap<>();
                        newSub.put("email", email);
                        return entityService.addItem("subscriber", ENTITY_VERSION, newSub, processSubscriber)
                                .thenApply(id -> {
                                    logger.info("Email subscribed successfully: {}", email);
                                    return ResponseEntity.ok(new SubscribeResponse("Subscription successful"));
                                });
                    }
                });
    }

    @GetMapping("/subscribers")
    public CompletableFuture<SubscribersResponse> getSubscribers() {
        logger.info("Fetching all subscribers");
        return entityService.getItems("subscriber", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<String> emails = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        JsonNode emailNode = node.get("email");
                        if (emailNode != null && emailNode.isTextual()) {
                            emails.add(emailNode.asText());
                        }
                    }
                    logger.info("Fetched subscribers count={}", emails.size());
                    return new SubscribersResponse(emails);
                });
    }

    @PostMapping("/fetch-scores")
    public CompletableFuture<ResponseEntity<FetchScoresResponse>> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Received fetch scores request for date: {}", request.getDate());
        Map<String, Object> fetchRequestEntity = new HashMap<>();
        fetchRequestEntity.put("date", request.getDate());
        return entityService.addItem("scoreFetchRequest", ENTITY_VERSION, fetchRequestEntity, processScoreFetchRequest)
                .thenApply(id -> ResponseEntity.ok(new FetchScoresResponse("Scores fetching started", request.getDate(), 0)));
    }

    @GetMapping("/games/all")
    public CompletableFuture<GamesResponse> getAllGames(
            @RequestParam(required = false, defaultValue = "1") @Min(1) Integer page,
            @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        logger.info("Fetching all games - page: {}, size: {}", page, size);
        return entityService.getItems("game", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Game> allGames = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        allGames.add(convertNodeToGame(node));
                    }
                    int total = allGames.size();
                    int from = (page - 1) * size;
                    if (from >= total) {
                        return new GamesResponse(Collections.emptyList(), page, size, total);
                    }
                    int to = Math.min(from + size, total);
                    return new GamesResponse(allGames.subList(from, to), page, size, total);
                });
    }

    @GetMapping("/games/{date}")
    public CompletableFuture<GamesByDateResponse> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be in YYYY-MM-DD format") String date
    ) {
        logger.info("Fetching games for date: {}", date);
        Condition condition = Condition.of("$.date", "EQUALS", date);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition("game", ENTITY_VERSION, searchCondition)
                .thenApply(arrayNode -> {
                    List<Game> games = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        games.add(convertNodeToGame(node));
                    }
                    return new GamesByDateResponse(date, games);
                });
    }

    private Game convertNodeToGame(JsonNode node) {
        String gameId = node.has("gameId") && node.get("gameId").isTextual() ? node.get("gameId").asText() : null;
        String date = node.has("date") && node.get("date").isTextual() ? node.get("date").asText() : null;
        String homeTeam = node.has("homeTeam") && node.get("homeTeam").isTextual() ? node.get("homeTeam").asText() : null;
        String awayTeam = node.has("awayTeam") && node.get("awayTeam").isTextual() ? node.get("awayTeam").asText() : null;
        Integer homeScore = node.has("homeScore") && node.get("homeScore").isInt() ? node.get("homeScore").asInt() : null;
        Integer awayScore = node.has("awayScore") && node.get("awayScore").isInt() ? node.get("awayScore").asInt() : null;
        UUID technicalId = null;
        if (node.has("technicalId") && node.get("technicalId").isTextual()) {
            try {
                technicalId = UUID.fromString(node.get("technicalId").asText());
            } catch (IllegalArgumentException ignored) {
            }
        }
        Game game = new Game(gameId, date, homeTeam, awayTeam, homeScore, awayScore);
        game.setTechnicalId(technicalId);
        return game;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String,String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleGenericException(Exception ex) {
        Map<String,String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}