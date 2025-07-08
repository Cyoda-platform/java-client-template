package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("cyoda-prototype")
@Validated
@Slf4j
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_NAME_GAME = "Game";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    // Workflow function for Subscriber entity
    // Normalize email, add subscribedAt if missing, and send async welcome email
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processSubscriber = entity -> {
        String email = entity.path("email").asText(null);
        if (email != null) {
            String normalizedEmail = email.toLowerCase(Locale.ROOT).trim();
            entity.put("email", normalizedEmail);
        }
        if (!entity.hasNonNull("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }
        return CompletableFuture.runAsync(() -> {
            logger.info("Sending welcome email to subscriber: {}", entity.get("email").asText());
            // Integrate actual email service here if needed
        }).thenApply(v -> entity);
    };

    // Workflow function for Game entity
    // Fill defaults and send async notifications to all subscribers after persistence
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processGame = entity -> {
        if (!entity.hasNonNull("status") || entity.get("status").asText().isEmpty()) {
            entity.put("status", "unknown");
        }
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenCompose(subscribersArray -> {
                    List<String> emails = new ArrayList<>();
                    for (JsonNode subscriberNode : subscribersArray) {
                        String email = subscriberNode.path("email").asText(null);
                        if (email != null) {
                            emails.add(email);
                        }
                    }
                    String summary = String.format("Game update: %s vs %s, score %d-%d, status: %s",
                            entity.path("awayTeam").asText(""),
                            entity.path("homeTeam").asText(""),
                            entity.path("awayScore").asInt(-1),
                            entity.path("homeScore").asInt(-1),
                            entity.path("status").asText(""));
                    List<CompletableFuture<Void>> emailFutures = new ArrayList<>();
                    for (String email : emails) {
                        emailFutures.add(CompletableFuture.runAsync(() -> {
                            logger.info("Sending game update email to {}: {}", email, summary);
                            // Integrate actual email service here if needed
                        }));
                    }
                    return CompletableFuture.allOf(emailFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> entity);
                });
    };

    @PostMapping("/subscribe")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> subscribe(@RequestBody @Valid SubscriptionRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT).trim();
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", email));
        return entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenCompose(arrayNode -> {
                    if (arrayNode.size() > 0) {
                        logger.info("Subscription attempt for existing email: {}", email);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.ok(new SubscriptionResponse("Email already subscribed", email))
                        );
                    } else {
                        ObjectNode subscriberNode = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
                        subscriberNode.put("email", email);
                        return entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriberNode, processSubscriber)
                                .thenApply(id -> ResponseEntity.ok(new SubscriptionResponse("Subscription successful", email)));
                    }
                });
    }

    @DeleteMapping("/subscribe")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> unsubscribe(@RequestParam @NotBlank @Email String email) {
        String cleanedEmail = email.toLowerCase(Locale.ROOT).trim();
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", cleanedEmail));
        return entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenCompose(arrayNode -> {
                    if (arrayNode.size() == 0) {
                        logger.info("Unsubscribe attempt for non-existing email: {}", cleanedEmail);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(new SubscriptionResponse("Email not found in subscription list", cleanedEmail))
                        );
                    } else {
                        List<CompletableFuture<UUID>> deleteFutures = new ArrayList<>();
                        for (JsonNode node : arrayNode) {
                            UUID technicalId = UUID.fromString(node.get("technicalId").asText());
                            deleteFutures.add(entityService.deleteItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, technicalId));
                        }
                        return CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> {
                                    logger.info("Subscriber(s) removed: {}", cleanedEmail);
                                    return ResponseEntity.ok(new SubscriptionResponse("Unsubscription successful", cleanedEmail));
                                });
                    }
                });
    }

    @GetMapping("/subscribers")
    public CompletableFuture<ResponseEntity<SubscribersResponse>> getSubscribers() {
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<String> emails = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        emails.add(node.path("email").asText());
                    }
                    logger.info("Retrieving all subscribers, count: {}", emails.size());
                    return ResponseEntity.ok(new SubscribersResponse(emails));
                });
    }

    // Fetch NBA games from external API and add each game with workflow to trigger notifications
    @PostMapping("/games/fetch")
    public CompletableFuture<ResponseEntity<GameFetchResponse>> fetchGames(@RequestBody @Valid GameFetchRequest request) {
        String date = request.getDate();
        logger.info("Fetching games for date {}", date);
        try {
            String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s", date, "test");
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return restTemplate.getForObject(new URI(url), String.class);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenCompose(response -> {
                if (response == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new GameFetchResponse("Failed to fetch games", date, 0)));
                }
                JsonNode rootNode;
                try {
                    rootNode = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response);
                } catch (Exception ex) {
                    logger.error("Failed to parse external API response", ex);
                    return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new GameFetchResponse("Invalid response format", date, 0)));
                }
                if (!rootNode.isArray()) {
                    return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new GameFetchResponse("Invalid response format", date, 0)));
                }
                List<CompletableFuture<UUID>> addFutures = new ArrayList<>();
                for (JsonNode node : rootNode) {
                    ObjectNode gameNode = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
                    gameNode.put("gameId", node.path("GameID").asText("unknown"));
                    gameNode.put("date", date);
                    gameNode.put("homeTeam", node.path("HomeTeam").asText("unknown"));
                    gameNode.put("awayTeam", node.path("AwayTeam").asText("unknown"));
                    gameNode.put("homeScore", node.path("HomeTeamScore").asInt(-1));
                    gameNode.put("awayScore", node.path("AwayTeamScore").asInt(-1));
                    gameNode.put("status", node.path("Status").asText("unknown"));
                    addFutures.add(entityService.addItem(ENTITY_NAME_GAME, ENTITY_VERSION, gameNode, processGame));
                }
                return CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0]))
                        .thenApply(v -> ResponseEntity.ok(new GameFetchResponse("Fetched and stored games", date, addFutures.size())));
            });
        } catch (Exception e) {
            logger.error("Error fetching games: {}", e.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GameFetchResponse("Error fetching games", date, 0)));
        }
    }

    @GetMapping("/games/all")
    public CompletableFuture<ResponseEntity<GamesResponse>> getAllGames(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        logger.info("Retrieving all games, page: {}, size: {}", page, size);
        return entityService.getItems(ENTITY_NAME_GAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Game> allGames = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        allGames.add(jsonNodeToGame(node));
                    }
                    int start = (page - 1) * size;
                    int end = Math.min(start + size, allGames.size());
                    if (start > allGames.size()) {
                        start = end = 0;
                    }
                    List<Game> paged = allGames.subList(start, end);
                    return ResponseEntity.ok(new GamesResponse(paged, page, size, allGames.size()));
                });
    }

    @GetMapping("/games/{date}")
    public CompletableFuture<ResponseEntity<GamesResponse>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Retrieving games for date: {}", date);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));
        return entityService.getItemsByCondition(ENTITY_NAME_GAME, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    List<Game> games = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        games.add(jsonNodeToGame(node));
                    }
                    return ResponseEntity.ok(new GamesResponse(games, 1, games.size(), games.size()));
                });
    }

    private Game jsonNodeToGame(JsonNode node) {
        Game g = new Game();
        g.setGameId(node.path("gameId").asText(null));
        g.setDate(node.path("date").asText(null));
        g.setHomeTeam(node.path("homeTeam").asText(null));
        g.setAwayTeam(node.path("awayTeam").asText(null));
        g.setHomeScore(node.path("homeScore").asInt(0));
        g.setAwayScore(node.path("awayScore").asInt(0));
        g.setStatus(node.path("status").asText(null));
        return g;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Unexpected error occurred");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscriptionRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscriptionResponse {
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribersResponse {
        private List<String> subscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameFetchRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameFetchResponse {
        private String message;
        private String date;
        private Integer gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Game {
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GamesResponse {
        private List<Game> games;
        private int page;
        private int size;
        private int total;
    }
}