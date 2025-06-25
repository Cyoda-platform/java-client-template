```java
package com.java_template.entity;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // In-memory subscriber store (email -> subscribed at)
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    // In-memory games store (date string -> List<Game>)
    private final Map<String, List<Game>> gamesStore = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String NBA_API_KEY = "test"; // TODO: Replace with real API key and secure storage
    private static final String NBA_API_URL_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + NBA_API_KEY;

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchGamesRequest {
        private String date; // YYYY-MM-DD
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchGamesResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private Date subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String otherInfo; // Raw JSON string for prototype
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

    // --- API Endpoints ---

    /**
     * Subscribe an email to daily notifications.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            logger.error("Subscription attempt with empty email");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be empty");
        }

        subscribers.putIfAbsent(email.toLowerCase(Locale.ROOT), new Subscriber(email, new Date()));
        logger.info("New subscription added: {}", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    /**
     * Retrieve all subscribed emails.
     */
    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        List<String> allEmails = new ArrayList<>(subscribers.keySet());
        logger.info("Retrieved {} subscribers", allEmails.size());
        return ResponseEntity.ok(new SubscribersResponse(allEmails));
    }

    /**
     * Retrieve all stored games with optional pagination.
     * For prototype, pagination is fake/simple.
     */
    @GetMapping("/games/all")
    public ResponseEntity<GamesResponse> getAllGames(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size) {

        List<Game> allGames = new ArrayList<>();
        gamesStore.values().forEach(allGames::addAll);
        int total = allGames.size();

        // Simple pagination logic for prototype
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Game> pageGames = allGames.subList(fromIndex, toIndex);

        logger.info("Retrieved all games page {} size {} total {}", page, size, total);
        return ResponseEntity.ok(new GamesResponse(pageGames, page, size, total));
    }

    /**
     * Retrieve all games for a specific date.
     */
    @GetMapping("/games/{date}")
    public ResponseEntity<GamesResponse> getGamesByDate(@PathVariable String date) {
        // Validate date format
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format requested: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        List<Game> games = gamesStore.getOrDefault(date, Collections.emptyList());
        logger.info("Retrieved {} games for date {}", games.size(), date);

        return ResponseEntity.ok(new GamesResponse(games, null, null, games.size()));
    }

    /**
     * Fetch NBA scores from external API, store them, and send notifications asynchronously.
     */
    @PostMapping("/games/fetch")
    public ResponseEntity<FetchGamesResponse> fetchAndStoreScores(@Valid @RequestBody FetchGamesRequest request) {
        String date = request.getDate();

        // Validate date format
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format for fetch: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        logger.info("Received request to fetch NBA scores for date {}", date);

        // Fire and forget async fetch/store/notify
        CompletableFuture.runAsync(() -> fetchStoreAndNotify(date));

        return ResponseEntity.ok(new FetchGamesResponse(
                "Scores fetched, saved, and notifications sent (async)", date, -1));
    }

    // --- Internal async processing ---

    @Async
    void fetchStoreAndNotify(String date) {
        try {
            logger.info("Starting fetchStoreAndNotify for date {}", date);
            String url = String.format(NBA_API_URL_TEMPLATE, date);
            String rawJson = restTemplate.getForObject(url, String.class);

            if (rawJson == null || rawJson.isBlank()) {
                logger.warn("Empty response from NBA API for date {}", date);
                return;
            }

            JsonNode rootNode = objectMapper.readTree(rawJson);

            if (!rootNode.isArray()) {
                logger.error("Unexpected NBA API response format for date {}: expected JSON array", date);
                return;
            }

            List<Game> fetchedGames = new ArrayList<>();
            for (JsonNode node : rootNode) {
                Game g = parseGameFromJsonNode(node, date);
                fetchedGames.add(g);
            }

            gamesStore.put(date, fetchedGames);
            logger.info("Stored {} games for date {}", fetchedGames.size(), date);

            // Notify subscribers asynchronously
            notifySubscribers(date, fetchedGames);

        } catch (Exception e) {
            logger.error("Error during fetchStoreAndNotify for date " + date, e);
        }
    }

    private Game parseGameFromJsonNode(JsonNode node, String date) {
        // Map fields with best guess; TODO: Adjust mapping as per real API response structure
        String homeTeam = node.path("HomeTeam").asText(null);
        String awayTeam = node.path("AwayTeam").asText(null);
        Integer homeScore = node.path("HomeTeamScore").isInt() ? node.path("HomeTeamScore").asInt() : null;
        Integer awayScore = node.path("AwayTeamScore").isInt() ? node.path("AwayTeamScore").asInt() : null;

        // Store raw JSON as string for prototype
        String otherInfo = node.toString();

        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, otherInfo);
    }

    private void notifySubscribers(String date, List<Game> games) {
        // TODO: Replace with real email sending service integration

        List<String> emails = new ArrayList<>(subscribers.keySet());
        if (emails.isEmpty()) {
            logger.info("No subscribers to notify for date {}", date);
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("NBA Scores for ").append(date).append(":\n");
        for (Game g : games) {
            summary.append(String.format("%s vs %s: %d - %d\n",
                    g.getHomeTeam(), g.getAwayTeam(),
                    Optional.ofNullable(g.getHomeScore()).orElse(-1),
                    Optional.ofNullable(g.getAwayScore()).orElse(-1)));
        }

        // Simulate sending emails
        for (String email : emails) {
            logger.info("Sending email to {}: \n{}", email, summary);
            // TODO: Integrate real email sending here (fire-and-forget)
        }
        logger.info("Email notifications sent to {} subscribers for date {}", emails.size(), date);
    }

    // --- Minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: {}", ex.getMessage());
        Map<String, Object> err = new HashMap<>();
        err.put("status", ex.getStatusCode().value());
        err.put("error", ex.getStatusCode().getReasonPhrase());
        err.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception caught: ", ex);
        Map<String, Object> err = new HashMap<>();
        err.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        err.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}
```
