```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + API_KEY;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mock DBs with thread-safe maps
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<GameScore>> gamesByDate = new ConcurrentHashMap<>();

    // ========== DTOs ==========

    @Data
    public static class SubscribeRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        private String date; // format YYYY-MM-DD
    }

    @Data
    public static class FetchScoresResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    public static class Subscriber {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class GameScore {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        // additional fields possible
    }

    // ========== API Endpoints ==========

    /**
     * POST /subscribe
     * Add a user email to subscription list. Duplicate emails ignored (idempotent).
     */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Validated SubscribeRequest request) {
        logger.info("Subscription request received for email: {}", request.getEmail());
        subscribers.putIfAbsent(request.getEmail().toLowerCase(Locale.ROOT), new Subscriber() {{
            setEmail(request.getEmail().toLowerCase(Locale.ROOT));
        }});

        SubscribeResponse response = new SubscribeResponse();
        response.setMessage("Subscription successful");
        response.setEmail(request.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /subscribers
     * Retrieve all subscribed emails.
     */
    @GetMapping("/subscribers")
    public ResponseEntity<Map<String, Collection<String>>> getSubscribers() {
        logger.info("Fetching all subscribers");
        return ResponseEntity.ok(Map.of("subscribers", subscribers.keySet()));
    }

    /**
     * POST /fetch-scores
     * Fetch NBA scores from external API for a given date, store, and notify subscribers.
     * This endpoint triggers external API call and notification asynchronously.
     */
    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Validated FetchScoresRequest request) {
        logger.info("Fetch scores request received for date: {}", request.getDate());

        // Validate date format (basic)
        LocalDate fetchDate;
        try {
            fetchDate = LocalDate.parse(request.getDate());
        } catch (Exception e) {
            logger.error("Invalid date format: {}", request.getDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        // Fire-and-forget async processing
        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(fetchDate);
            } catch (Exception ex) {
                logger.error("Error during fetchStoreAndNotify for date {}: {}", fetchDate, ex.getMessage(), ex);
            }
        });
        // Respond immediately
        FetchScoresResponse response = new FetchScoresResponse();
        response.setMessage("Scores fetching started asynchronously");
        response.setDate(request.getDate());
        response.setGamesCount(0); // unknown now, will update after processing
        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET /games/all
     * Retrieve all stored games with optional pagination.
     */
    @GetMapping("/games/all")
    public ResponseEntity<Map<String, Object>> getAllGames(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        logger.info("Fetching all games with limit {} and offset {}", limit, offset);
        // Aggregate all stored games from all dates
        List<GameScore> allGames = new ArrayList<>();
        for (List<GameScore> games : gamesByDate.values()) {
            allGames.addAll(games);
        }
        allGames.sort(Comparator.comparing(GameScore::getDate).reversed()); // newest first

        int total = allGames.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<GameScore> page = allGames.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("games", page);
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("total", total);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /games/{date}
     * Retrieve all NBA games for a specific date.
     */
    @GetMapping("/games/{date}")
    public ResponseEntity<List<GameScore>> getGamesByDate(@PathVariable String date) {
        logger.info("Fetching games for date: {}", date);
        List<GameScore> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(games);
    }

    // ========== Internal Async Processing ==========

    private void fetchStoreAndNotify(LocalDate date) throws Exception {
        String dateStr = date.toString();
        logger.info("Starting fetchStoreAndNotify for date {}", dateStr);

        // 1) Fetch external NBA scores JSON
        String url = String.format(NBA_API_URL_TEMPLATE, dateStr);
        logger.info("Calling external NBA API: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.error("External API call failed with status: {}", response.statusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch NBA scores");
        }

        String body = response.body();
        JsonNode rootNode = objectMapper.readTree(body);

        // 2) Parse and store games
        List<GameScore> parsedGames = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode gameNode : rootNode) {
                GameScore game = new GameScore();
                game.setDate(dateStr);
                game.setHomeTeam(getTextOrNull(gameNode, "HomeTeam"));
                game.setAwayTeam(getTextOrNull(gameNode, "AwayTeam"));
                game.setHomeScore(getIntOrNull(gameNode, "HomeTeamScore"));
                game.setAwayScore(getIntOrNull(gameNode, "AwayTeamScore"));
                parsedGames.add(game);
            }
        } else {
            logger.warn("Expected JSON array but got different structure");
        }

        gamesByDate.put(dateStr, parsedGames);
        logger.info("Stored {} games for date {}", parsedGames.size(), dateStr);

        // 3) Send email notifications to all subscribers with summary
        sendEmailNotifications(dateStr, parsedGames);
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Integer getIntOrNull(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && f.isInt()) ? f.asInt() : null;
    }

    private void sendEmailNotifications(String date, List<GameScore> games) {
        logger.info("Sending email notifications to {} subscribers", subscribers.size());

        // TODO: Replace this with real email service integration
        StringBuilder summary = new StringBuilder();
        summary.append("NBA Daily Scores for ").append(date).append(":\n\n");
        for (GameScore game : games) {
            summary.append(String.format("%s vs %s: %d - %d\n",
                    game.getHomeTeam(),
                    game.getAwayTeam(),
                    Optional.ofNullable(game.getHomeScore()).orElse(0),
                    Optional.ofNullable(game.getAwayScore()).orElse(0)));
        }

        for (Subscriber subscriber : subscribers.values()) {
            // Fire-and-forget async email sending simulation
            CompletableFuture.runAsync(() -> {
                logger.info("Sending email to {}:\n{}", subscriber.getEmail(), summary);
                // TODO: Integrate real email sending here
            });
        }
    }

    // ========== Error Handling ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        Map<String, String> error = Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```