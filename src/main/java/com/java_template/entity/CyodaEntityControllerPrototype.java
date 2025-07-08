package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    @PostMapping("/subscribe")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> subscribe(@RequestBody @Valid SubscriptionRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT).trim();

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", email));

        return entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    if (arrayNode.size() > 0) {
                        logger.info("Subscription attempt for existing email: {}", email);
                        return ResponseEntity.ok(new SubscriptionResponse("Email already subscribed", email));
                    } else {
                        Subscriber subscriber = new Subscriber(email, Instant.now());
                        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber);
                        return idFuture.thenApply(id -> {
                            logger.info("New subscriber added: {}", email);
                            return ResponseEntity.ok(new SubscriptionResponse("Subscription successful", email));
                        }).join();
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
                        // there could be multiple but we remove all found
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

    @PostMapping("/games/fetch")
    public ResponseEntity<GameFetchResponse> fetchGames(@RequestBody @Valid GameFetchRequest request) {
        String date = request.getDate();
        logger.info("Received request to fetch games for date: {}", date);
        fetchAndStoreGamesAsync(date);
        return ResponseEntity.ok(new GameFetchResponse("Game data fetch triggered", date, null));
    }

    @Async
    public CompletableFuture<Void> fetchAndStoreGamesAsync(String date) {
        try {
            String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s", date, "test");
            logger.info("Calling external API: {}", url);
            String response = restTemplate.getForObject(new URI(url), String.class);
            if (response == null) {
                logger.error("External API returned null response");
                return CompletableFuture.completedFuture(null);
            }
            JsonNode rootNode = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response);
            if (!rootNode.isArray()) {
                logger.error("Unexpected response format from external API (not an array)");
                return CompletableFuture.completedFuture(null);
            }
            List<Game> gamesList = new ArrayList<>();
            for (JsonNode node : rootNode) {
                Game g = new Game();
                g.setGameId(node.path("GameID").asText("unknown"));
                g.setDate(date);
                g.setHomeTeam(node.path("HomeTeam").asText("unknown"));
                g.setAwayTeam(node.path("AwayTeam").asText("unknown"));
                g.setHomeScore(node.path("HomeTeamScore").asInt(-1));
                g.setAwayScore(node.path("AwayTeamScore").asInt(-1));
                g.setStatus(node.path("Status").asText("unknown"));
                gamesList.add(g);
            }
            CompletableFuture<List<UUID>> addItemsFuture = entityService.addItems(ENTITY_NAME_GAME, ENTITY_VERSION, gamesList);
            addItemsFuture.thenRun(() -> {
                logger.info("Stored {} games for date {}", gamesList.size(), date);
                sendEmailNotifications(date, gamesList);
            });
        } catch (URISyntaxException e) {
            logger.error("Invalid URI for external API call: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching or processing external API response: {}", e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
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
                        Game g = jsonNodeToGame(node);
                        allGames.add(g);
                    }
                    int start = (page - 1) * size;
                    int end = Math.min(start + size, allGames.size());
                    if (start > allGames.size()) { start = end = 0; }
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

    private void sendEmailNotifications(String date, List<Game> games) {
        logger.info("Sending email notifications for date {}, to {} subscribers", date, 0); // subscribers count unknown here
        StringBuilder summary = new StringBuilder("NBA Scores for " + date + ":\n");
        for (Game g : games) {
            summary.append(String.format("%s vs %s: %d - %d (%s)\n",
                    g.getAwayTeam(), g.getHomeTeam(), g.getAwayScore(), g.getHomeScore(), g.getStatus()));
        }
        // As subscribers are stored externally, fetch subscribers to send notifications
        entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).thenAccept(arrayNode -> {
            logger.info("Sending email notifications for date {}, to {} subscribers", date, arrayNode.size());
            for (JsonNode node : arrayNode) {
                String email = node.path("email").asText();
                logger.info("Email to {}:\n{}", email, summary);
            }
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
        @NotBlank @Email private String email;
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
    static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameFetchRequest {
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String date;
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