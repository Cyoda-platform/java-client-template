```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.constraints.Email;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype")
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Mocked persistence - thread-safe maps
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    // DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        private String date; // YYYY-MM-DD
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Game {
        private String gameId;
        private String date; // YYYY-MM-DD
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        // TODO: Add other relevant fields if needed
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
    static class GamesResponse {
        private List<Game> games;
        private Integer page;
        private Integer size;
        private Integer total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GamesByDateResponse {
        private String date;
        private List<Game> games;
    }

    // --- Endpoints ---

    /**
     * Subscribe endpoint - add subscriber email
     */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());

        if (!StringUtils.hasText(request.getEmail()) || !request.getEmail().matches("^[\\w-.]+@[\\w-]+\\.[a-z]{2,}$")) {
            logger.error("Invalid email format: {}", request.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
        }

        if (subscribers.containsKey(request.getEmail().toLowerCase())) {
            logger.info("Email already subscribed: {}", request.getEmail());
            return ResponseEntity.ok(new SubscribeResponse("Email already subscribed"));
        }

        subscribers.put(request.getEmail().toLowerCase(), new Subscriber(request.getEmail().toLowerCase()));

        logger.info("Email subscribed successfully: {}", request.getEmail());
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful"));
    }

    /**
     * Get all subscribers
     */
    @GetMapping("/subscribers")
    public SubscribersResponse getSubscribers() {
        logger.info("Fetching all subscribers, count={}", subscribers.size());
        return new SubscribersResponse(new ArrayList<>(subscribers.keySet()));
    }

    /**
     * Fetch NBA scores for given date, store locally, and notify subscribers
     */
    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody FetchScoresRequest request) {
        logger.info("Fetch scores requested for date: {}", request.getDate());

        String dateStr = request.getDate();
        if (!isValidDate(dateStr)) {
            logger.error("Invalid date format: {}", dateStr);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        // Fire and forget async fetch/store/notify
        CompletableFuture.runAsync(() -> fetchStoreNotify(dateStr));

        // Immediately return processing message
        return ResponseEntity.ok(new FetchScoresResponse("Scores fetching started", dateStr, 0));
    }

    /**
     * Get all stored games with optional pagination
     */
    @GetMapping("/games/all")
    public GamesResponse getAllGames(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        logger.info("Fetching all games - page: {}, size: {}", page, size);

        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);

        int total = allGames.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        if (fromIndex >= total || fromIndex < 0) {
            logger.warn("Page out of range: {}", page);
            return new GamesResponse(Collections.emptyList(), page, size, total);
        }

        List<Game> pagedGames = allGames.subList(fromIndex, toIndex);

        return new GamesResponse(pagedGames, page, size, total);
    }

    /**
     * Get games by date
     */
    @GetMapping("/games/{date}")
    public GamesByDateResponse getGamesByDate(@PathVariable String date) {
        logger.info("Fetching games for date: {}", date);

        if (!isValidDate(date)) {
            logger.error("Invalid date format: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());

        return new GamesByDateResponse(date, games);
    }

    // --- Helper methods ---

    private boolean isValidDate(String dateStr) {
        try {
            LocalDate.parse(dateStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Async fetch, store, and notify process for given date
     * TODO: Replace email sending and database storage with real implementations
     */
    @Async
    void fetchStoreNotify(String dateStr) {
        logger.info("Starting async fetch-store-notify for date: {}", dateStr);
        try {
            // External NBA API endpoint
            // TODO: Replace "test" API key with real secured key in production
            String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test", dateStr);

            logger.info("Calling external NBA API: {}", url);
            String jsonResponse = restTemplate.getForObject(new URI(url), String.class);

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (rootNode == null || !rootNode.isArray()) {
                logger.error("Unexpected response format from NBA API");
                return;
            }

            List<Game> fetchedGames = new ArrayList<>();
            for (JsonNode gameNode : rootNode) {
                // Extract fields with safe defaults
                String gameId = gameNode.path("GameID").asText("");
                String homeTeam = gameNode.path("HomeTeam").asText("");
                String awayTeam = gameNode.path("AwayTeam").asText("");
                Integer homeScore = gameNode.path("HomeTeamScore").isInt() ? gameNode.path("HomeTeamScore").asInt() : null;
                Integer awayScore = gameNode.path("AwayTeamScore").isInt() ? gameNode.path("AwayTeamScore").asInt() : null;

                Game game = new Game(gameId, dateStr, homeTeam, awayTeam, homeScore, awayScore);
                fetchedGames.add(game);
            }

            // Store games locally (mock)
            gamesByDate.put(dateStr, fetchedGames);
            logger.info("Stored {} games for date {}", fetchedGames.size(), dateStr);

            // Notify subscribers with summary email (mock)
            notifySubscribers(dateStr, fetchedGames);

            logger.info("Completed async fetch-store-notify for date: {}", dateStr);
        } catch (URISyntaxException e) {
            logger.error("Invalid URI syntax for NBA API", e);
        } catch (Exception e) {
            logger.error("Error during fetch-store-notify process", e);
        }
    }

    /**
     * Mock email notification to all subscribers with daily summary
     * TODO: Replace this with real email sending service
     */
    private void notifySubscribers(String dateStr, List<Game> games) {
        if (subscribers.isEmpty()) {
            logger.info("No subscribers to notify for date {}", dateStr);
            return;
        }

        StringBuilder emailContent = new StringBuilder();
        emailContent.append("Daily NBA Scores for ").append(dateStr).append(":\n\n");

        for (Game game : games) {
            emailContent
                    .append(game.getAwayTeam())
                    .append(" @ ")
                    .append(game.getHomeTeam())
                    .append(": ")
                    .append(Objects.toString(game.getAwayScore(), "N/A"))
                    .append(" - ")
                    .append(Objects.toString(game.getHomeScore(), "N/A"))
                    .append("\n");
        }

        subscribers.values().forEach(sub -> {
            // TODO: Send real email here
            logger.info("Sending email to {}:\n{}", sub.getEmail(), emailContent.toString());
        });
    }

    // --- Minimal exception handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("ResponseStatusException: {}", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        logger.error("Unhandled exception", ex);
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```