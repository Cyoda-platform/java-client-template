package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("/cyodaEntityPrototype")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_NAME_GAME = "Game";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class SubscribeResponse {
        private String message;
        private String email;

        public SubscribeResponse() {}

        public SubscribeResponse(String message, String email) {
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

        public SubscribersResponse() {}

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

    public static class FetchScoresRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
        private String date;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    public static class FetchScoresResponse {
        private String message;
        private String date;
        private int gamesCount;

        public FetchScoresResponse() {}

        public FetchScoresResponse(String message, String date, int gamesCount) {
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

        public int getGamesCount() {
            return gamesCount;
        }

        public void setGamesCount(int gamesCount) {
            this.gamesCount = gamesCount;
        }
    }

    public static class GamesResponse {
        private int page;
        private int size;
        private long totalGames;
        private List<Game> games;

        public GamesResponse() {}

        public GamesResponse(int page, int size, long totalGames, List<Game> games) {
            this.page = page;
            this.size = size;
            this.totalGames = totalGames;
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

        public long getTotalGames() {
            return totalGames;
        }

        public void setTotalGames(long totalGames) {
            this.totalGames = totalGames;
        }

        public List<Game> getGames() {
            return games;
        }

        public void setGames(List<Game> games) {
            this.games = games;
        }
    }

    public static class GamesByDateResponse {
        private String date;
        private List<Game> games;

        public GamesByDateResponse() {}

        public GamesByDateResponse(String date, List<Game> games) {
            this.date = date;
            this.games = games;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public List<Game> getGames() {
            return games;
        }

        public void setGames(List<Game> games) {
            this.games = games;
        }
    }

    public static class Subscriber {
        private String email;
        private Instant subscribedAt;

        public Subscriber() {}

        public Subscriber(String email, Instant subscribedAt) {
            this.email = email;
            this.subscribedAt = subscribedAt;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public Instant getSubscribedAt() {
            return subscribedAt;
        }

        public void setSubscribedAt(Instant subscribedAt) {
            this.subscribedAt = subscribedAt;
        }
    }

    public static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;

        public Game() {}

        public Game(String date, String homeTeam, String awayTeam, Integer homeScore, Integer awayScore, String status) {
            this.date = date;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.status = status;
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

        public Integer getHomeScore() {
            return homeScore;
        }

        public void setHomeScore(Integer homeScore) {
            this.homeScore = homeScore;
        }

        public Integer getAwayScore() {
            return awayScore;
        }

        public void setAwayScore(Integer awayScore) {
            this.awayScore = awayScore;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) throws ExecutionException, InterruptedException {
        String email = request.getEmail().toLowerCase(Locale.ROOT);

        Condition condition = Condition.of("$.email", "EQUALS", email);
        SearchConditionRequest condRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> existingSubsFuture = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condRequest);
        ArrayNode existingSubs = existingSubsFuture.get();

        if (existingSubs != null && existingSubs.size() > 0) {
            logger.info("Subscription attempt for existing email: {}", email);
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", email));
        }

        Subscriber newSubscriber = new Subscriber(email, Instant.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, newSubscriber);
        idFuture.get();

        logger.info("New subscriber added: {}", email);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubscribeResponse("Subscription successful", email));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> deleteSubscription(@Valid @RequestBody SubscribeRequest request) throws ExecutionException, InterruptedException {
        String email = request.getEmail().toLowerCase(Locale.ROOT);

        Condition condition = Condition.of("$.email", "EQUALS", email);
        SearchConditionRequest condRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> existingSubsFuture = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condRequest);
        ArrayNode existingSubs = existingSubsFuture.get();

        if (existingSubs == null || existingSubs.size() == 0) {
            logger.info("Delete subscription attempt for non-existing email: {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SubscribeResponse("Email not found in subscribers", email));
        }

        for (JsonNode subscriberNode : existingSubs) {
            UUID technicalId = UUID.fromString(subscriberNode.get("technicalId").asText());
            entityService.deleteItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, technicalId).get();
        }
        logger.info("Subscriber removed: {}", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription deleted", email));
    }

    @GetMapping("/subscribers")
    public SubscribersResponse getSubscribers() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subs = subsFuture.get();
        List<String> emails = new ArrayList<>();
        if (subs != null) {
            for (JsonNode node : subs) {
                JsonNode emailNode = node.get("email");
                if (emailNode != null && !emailNode.isNull()) {
                    emails.add(emailNode.asText());
                }
            }
        }
        logger.info("Retrieving all subscribers, count={}", emails.size());
        return new SubscribersResponse(emails);
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchScoresResponse> fetchAndStoreScores(@Valid @RequestBody FetchScoresRequest request) throws Exception {
        String dateStr = (request.getDate() != null) ? request.getDate() : java.time.LocalDate.now().toString();
        logger.info("Fetching NBA scores for date: {}", dateStr);

        List<Game> fetchedGames;
        try {
            fetchedGames = fetchScoresFromExternalApi(dateStr);
        } catch (Exception ex) {
            logger.error("Failed to fetch NBA scores from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch from external API");
        }

        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (Game game : fetchedGames) {
            futures.add(entityService.addItem(ENTITY_NAME_GAME, ENTITY_VERSION, game));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        logger.info("Stored {} games for date {}", fetchedGames.size(), dateStr);

        return ResponseEntity.ok(new FetchScoresResponse("Scores fetched and stored", dateStr, fetchedGames.size()));
    }

    @GetMapping("/games/all")
    public GamesResponse getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) throws ExecutionException, InterruptedException {

        logger.info("Retrieving all games - page: {}, size: {}", page, size);
        CompletableFuture<ArrayNode> gamesFuture = entityService.getItems(ENTITY_NAME_GAME, ENTITY_VERSION);
        ArrayNode gamesArray = gamesFuture.get();

        List<Game> allGames = new ArrayList<>();
        if (gamesArray != null) {
            for (JsonNode node : gamesArray) {
                allGames.add(parseGameNode(node));
            }
        }

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allGames.size());
        List<Game> pageGames = fromIndex >= allGames.size() ? Collections.emptyList() : allGames.subList(fromIndex, toIndex);
        return new GamesResponse(page, size, allGames.size(), pageGames);
    }

    @GetMapping("/games/{date}")
    public GamesByDateResponse getGamesByDate(
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
            @PathVariable String date) throws ExecutionException, InterruptedException {
        logger.info("Retrieving games for date {}", date);

        Condition condition = Condition.of("$.date", "EQUALS", date);
        SearchConditionRequest condRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> filteredGamesFuture = entityService.getItemsByCondition(ENTITY_NAME_GAME, ENTITY_VERSION, condRequest);
        ArrayNode filteredGamesArray = filteredGamesFuture.get();

        List<Game> games = new ArrayList<>();
        if (filteredGamesArray != null) {
            for (JsonNode node : filteredGamesArray) {
                games.add(parseGameNode(node));
            }
        }
        return new GamesByDateResponse(date, games);
    }

    private List<Game> fetchScoresFromExternalApi(String date) throws Exception {
        String url = String.format(EXTERNAL_API_URL_TEMPLATE, date, API_KEY);
        logger.info("Calling external API: {}", url);
        String rawJson = restTemplate.getForObject(new URI(url), String.class);
        if (rawJson == null) throw new IllegalStateException("Empty response");
        JsonNode rootNode = objectMapper.readTree(rawJson);
        List<Game> gameList = new ArrayList<>();
        if (rootNode.isArray()) {
            for (JsonNode gameNode : rootNode) {
                gameList.add(parseGame(gameNode, date));
            }
        } else if (rootNode.isObject()) {
            gameList.add(parseGame(rootNode, date));
        }
        return gameList;
    }

    private Game parseGame(JsonNode node, String date) {
        return new Game(
                date,
                safeText(node, "HomeTeam"),
                safeText(node, "AwayTeam"),
                safeInt(node, "HomeTeamScore"),
                safeInt(node, "AwayTeamScore"),
                safeText(node, "Status")
        );
    }

    private Game parseGameNode(JsonNode node) {
        String date = safeText(node, "date");
        String homeTeam = safeText(node, "homeTeam");
        String awayTeam = safeText(node, "awayTeam");
        Integer homeScore = safeInt(node, "homeScore");
        Integer awayScore = safeInt(node, "awayScore");
        String status = safeText(node, "status");

        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, status);
    }

    private String safeText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private Integer safeInt(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && child.isInt()) return child.asInt();
        if (child != null && child.isTextual()) {
            try {
                return Integer.parseInt(child.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "INTERNAL_SERVER_ERROR");
        error.put("message", ex.getMessage());
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}