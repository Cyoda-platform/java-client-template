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

import javax.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage mocks:
    // Key: email, Value: Subscription
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    // Key: gameId (UUID), Value: GameScore
    private final Map<UUID, GameScore> storedGames = new ConcurrentHashMap<>();

    // ============================
    // DTOs / Entities
    // ============================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscription {
        private String email;
        private OffsetDateTime subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameScore {
        private UUID id;
        private LocalDate date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
        // Add other relevant info as required
    }

    @Data
    static class SubscribeRequest {
        private String email;
    }

    @Data
    static class FetchScoresRequest {
        private String date; // optional YYYY-MM-DD
    }

    // ============================
    // API Endpoints
    // ============================

    /**
     * POST /prototype/subscribe
     * Subscribe user email for daily notifications.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail();
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            logger.warn("Invalid subscription attempt with email: {}", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        }
        if (subscriptions.containsKey(email.toLowerCase())) {
            logger.info("Duplicate subscription attempt for email: {}", email);
            // Idempotent - treat as success
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Subscription already exists."
            ));
        }
        Subscription sub = new Subscription(email.toLowerCase(), OffsetDateTime.now());
        subscriptions.put(email.toLowerCase(), sub);
        logger.info("New subscription added: {}", email);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Subscription added."
        ));
    }

    /**
     * GET /prototype/subscribers
     * Return list of all subscribed emails.
     */
    @GetMapping("/subscribers")
    public ResponseEntity<Map<String, List<String>>> getSubscribers() {
        List<String> emails = new ArrayList<>(subscriptions.keySet());
        logger.info("Returning {} subscribers", emails.size());
        return ResponseEntity.ok(Map.of("subscribers", emails));
    }

    /**
     * POST /prototype/fetch-scores
     * Fetch NBA scores for given date (or today), save locally, send notifications.
     * This is fire-and-forget async.
     */
    @PostMapping("/fetch-scores")
    public ResponseEntity<Map<String, String>> fetchScores(@RequestBody(required = false) FetchScoresRequest request) {
        String dateStr = null;
        if (request != null) {
            dateStr = request.getDate();
        }
        LocalDate date;
        try {
            date = dateStr == null || dateStr.isBlank()
                    ? LocalDate.now()
                    : LocalDate.parse(dateStr);
        } catch (Exception ex) {
            logger.error("Invalid date format in fetchScores request: {}", dateStr);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }

        logger.info("Triggered fetchScores for date {}", date);

        // Fire-and-forget the actual fetch, store, notify process
        CompletableFuture.runAsync(() -> fetchStoreAndNotify(date));

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Scores fetch and notification process started."
        ));
    }

    /**
     * GET /prototype/games/all
     * Return all stored games, with optional filtering and pagination.
     * Query params: page, size, team, dateFrom, dateTo
     */
    @GetMapping("/games/all")
    public ResponseEntity<Map<String, Object>> getAllGames(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        logger.info("Fetching all games with filters page={}, size={}, team={}, dateFrom={}, dateTo={}",
                page, size, team, dateFrom, dateTo);

        LocalDate fromDate = null;
        LocalDate toDate = null;
        try {
            if (dateFrom != null && !dateFrom.isBlank()) fromDate = LocalDate.parse(dateFrom);
            if (dateTo != null && !dateTo.isBlank()) toDate = LocalDate.parse(dateTo);
        } catch (Exception ex) {
            logger.error("Invalid dateFrom/dateTo format: dateFrom={}, dateTo={}", dateFrom, dateTo);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be YYYY-MM-DD");
        }

        List<GameScore> filtered = new ArrayList<>();
        for (GameScore game : storedGames.values()) {
            boolean matches = true;
            if (team != null && !team.isBlank()) {
                String teamLower = team.toLowerCase();
                if (!game.getHomeTeam().toLowerCase().contains(teamLower) &&
                        !game.getAwayTeam().toLowerCase().contains(teamLower)) {
                    matches = false;
                }
            }
            if (fromDate != null && game.getDate().isBefore(fromDate)) matches = false;
            if (toDate != null && game.getDate().isAfter(toDate)) matches = false;
            if (matches) filtered.add(game);
        }

        // Pagination
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, filtered.size());
        if (fromIndex > filtered.size()) {
            fromIndex = filtered.size();
            toIndex = filtered.size();
        }
        List<GameScore> pageContent = filtered.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("games", pageContent);
        response.put("pagination", Map.of(
                "page", page,
                "size", size,
                "totalPages", (int) Math.ceil((double) filtered.size() / size),
                "totalElements", filtered.size()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * GET /prototype/games/{date}
     * Return all games for a specific date.
     */
    @GetMapping("/games/{date}")
    public ResponseEntity<Map<String, List<GameScore>>> getGamesByDate(@PathVariable String date) {
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date);
        } catch (Exception ex) {
            logger.error("Invalid date format for getGamesByDate: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }
        List<GameScore> result = new ArrayList<>();
        for (GameScore game : storedGames.values()) {
            if (game.getDate().equals(localDate)) {
                result.add(game);
            }
        }
        logger.info("Returning {} games for date {}", result.size(), date);
        return ResponseEntity.ok(Map.of("games", result));
    }

    // ============================
    // Internal logic (mocked)
    // ============================

    /**
     * Fetch scores from external API, store them, and send notifications.
     * Runs asynchronously in fire-and-forget style.
     */
    @Async
    void fetchStoreAndNotify(LocalDate date) {
        logger.info("Started fetchStoreAndNotify for date {}", date);
        try {
            // Build external API URL
            String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date.toString() + "?key=test";

            logger.info("Calling external NBA API: {}", url);
            String rawJson = restTemplate.getForObject(new URI(url), String.class);

            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                logger.warn("Unexpected JSON response format: expected array but got {}", rootNode.getNodeType());
                return;
            }

            int savedCount = 0;
            for (JsonNode gameNode : rootNode) {
                // Extract relevant fields with fallback to default values
                String homeTeam = gameNode.path("HomeTeam").asText(null);
                String awayTeam = gameNode.path("AwayTeam").asText(null);
                Integer homeScore = gameNode.path("HomeTeamScore").isInt() ? gameNode.path("HomeTeamScore").asInt() : null;
                Integer awayScore = gameNode.path("AwayTeamScore").isInt() ? gameNode.path("AwayTeamScore").asInt() : null;
                String status = gameNode.path("Status").asText(null);

                if (homeTeam == null || awayTeam == null) {
                    logger.warn("Skipping game due to missing team info: {}", gameNode.toString());
                    continue;
                }

                GameScore game = new GameScore(
                        UUID.randomUUID(),
                        date,
                        homeTeam,
                        awayTeam,
                        homeScore,
                        awayScore,
                        status
                );

                storedGames.put(game.getId(), game);
                savedCount++;
            }

            logger.info("Stored {} games for date {}", savedCount, date);

            // Send notifications to subscribers
            notifySubscribers(date);

        } catch (Exception ex) {
            logger.error("Error in fetchStoreAndNotify: ", ex);
        }
    }

    /**
     * Mock sending email notifications to all subscribers.
     * TODO: Replace with real email sending logic.
     */
    private void notifySubscribers(LocalDate date) {
        // Gather scores summary
        List<GameScore> gamesForDate = new ArrayList<>();
        for (GameScore game : storedGames.values()) {
            if (game.getDate().equals(date)) {
                gamesForDate.add(game);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Daily NBA Scores for ").append(date).append(":\n\n");
        for (GameScore game : gamesForDate) {
            sb.append(String.format("%s vs %s: %d - %d | Status: %s\n",
                    game.getAwayTeam(), game.getHomeTeam(),
                    Optional.ofNullable(game.getAwayScore()).orElse(-1),
                    Optional.ofNullable(game.getHomeScore()).orElse(-1),
                    Optional.ofNullable(game.getStatus()).orElse("N/A")));
        }

        String message = sb.toString();

        subscriptions.values().forEach(sub -> {
            // TODO: Replace this with real async email sending
            logger.info("Sending email to {} with content:\n{}", sub.getEmail(), message);
        });

        logger.info("Notifications sent to {} subscribers", subscriptions.size());
    }

    // ============================
    // Error handling
    // ============================

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        ));
    }
}
```