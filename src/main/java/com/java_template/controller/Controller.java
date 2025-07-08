package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("cyoda-prototype")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_NAME_GAME = "Game";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        logger.info("Controller initialized");
    }

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
                        ObjectNode subscriberNode = objectMapper.createObjectNode();
                        subscriberNode.put("email", email);
                        return entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriberNode);
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
                    rootNode = objectMapper.readTree(response);
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
                    ObjectNode gameNode = objectMapper.createObjectNode();
                    gameNode.put("gameId", node.path("GameID").asText("unknown"));
                    gameNode.put("date", date);
                    gameNode.put("homeTeam", node.path("HomeTeam").asText("unknown"));
                    gameNode.put("awayTeam", node.path("AwayTeam").asText("unknown"));
                    gameNode.put("homeScore", node.path("HomeTeamScore").asInt(-1));
                    gameNode.put("awayScore", node.path("AwayTeamScore").asInt(-1));
                    gameNode.put("status", node.path("Status").asText("unknown"));
                    addFutures.add(entityService.addItem(ENTITY_NAME_GAME, ENTITY_VERSION, gameNode));
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

    // DTO and internal classes

    public static class SubscriptionRequest {
        @NotBlank
        @Email
        private String email;

        public SubscriptionRequest() {
        }

        public SubscriptionRequest(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class SubscriptionResponse {
        private String message;
        private String email;

        public SubscriptionResponse() {
        }

        public SubscriptionResponse(String message, String email) {
            this.message = message;
            this.email = email;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class SubscribersResponse {
        private List<String> subscribers;

        public SubscribersResponse() {
        }

        public SubscribersResponse(List<String> subscribers) {
            this.subscribers = subscribers;
        }

        public List<String> getSubscribers() {
            return subscribers;
        }

        public void setSubscribers(List<String> subscribers) {
            this.subscribers = subscribers;
        }
    }

    public static class GameFetchRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;

        public GameFetchRequest() {
        }

        public GameFetchRequest(String date) {
            this.date = date;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    public static class GameFetchResponse {
        private String message;
        private String date;
        private Integer gamesCount;

        public GameFetchResponse() {
        }

        public GameFetchResponse(String message, String date, Integer gamesCount) {
            this.message = message;
            this.date = date;
            this.gamesCount = gamesCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Integer getGamesCount() {
            return gamesCount;
        }

        public void setGamesCount(Integer gamesCount) {
            this.gamesCount = gamesCount;
        }
    }

    public static class Game {
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
        private String status;

        public Game() {
        }

        public Game(String gameId, String date, String homeTeam, String awayTeam, int homeScore, int awayScore, String status) {
            this.gameId = gameId;
            this.date = date;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.status = status;
        }

        public String getGameId() {
            return gameId;
        }

        public void setGameId(String gameId) {
            this.gameId = gameId;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getHomeTeam() {
            return homeTeam;
        }

        public void setHomeTeam(String homeTeam) {
            this.homeTeam = homeTeam;
        }

        public String getAwayTeam() {
            return awayTeam;
        }

        public void setAwayTeam(String awayTeam) {
            this.awayTeam = awayTeam;
        }

        public int getHomeScore() {
            return homeScore;
        }

        public void setHomeScore(int homeScore) {
            this.homeScore = homeScore;
        }

        public int getAwayScore() {
            return awayScore;
        }

        public void setAwayScore(int awayScore) {
            this.awayScore = awayScore;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class GamesResponse {
        private List<Game> games;
        private int page;
        private int size;
        private int total;

        public GamesResponse() {
        }

        public GamesResponse(List<Game> games, int page, int size, int total) {
            this.games = games;
            this.page = page;
            this.size = size;
            this.total = total;
        }

        public List<Game> getGames() {
            return games;
        }

        public void setGames(List<Game> games) {
            this.games = games;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }
}