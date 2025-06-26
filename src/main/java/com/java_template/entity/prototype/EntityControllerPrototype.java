```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final String NBA_API_KEY = "test"; // TODO: Replace with config/env variable
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory mock DB for subscribers (email -> subscription date)
    private final Map<String, LocalDate> subscribers = new ConcurrentHashMap<>();

    // In-memory mock DB for games: date string -> list of games
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    // ========== API DTOs ==========

    @Data
    public static class SubscribeRequest {
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status; // e.g. "Final"
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data
    public static class FetchScoresRequest {
        private String date; // YYYY-MM-DD
    }

    // ========== Controller Methods ==========

    /**
     * Subscribe user by email for daily notifications.
     */
    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> subscribe(@RequestBody SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());

        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be empty");
        }
        // Basic email format validation is done via @Email annotation, but no automatic validation here,
        // so check manually:
        if (!request.getEmail().matches("^[\\w-.]+@[\\w-]+(\\.[\\w-]+)+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
        }

        // Add subscriber if not present
        subscribers.putIfAbsent(request.getEmail().toLowerCase(Locale.ROOT), LocalDate.now());

        logger.info("Subscription successful for email: {}", request.getEmail());
        return ResponseEntity.ok(new MessageResponse("Subscription successful"));
    }

    /**
     * Retrieve list of all subscribed emails.
     */
    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getSubscribers() {
        logger.info("Retrieving all subscribers");
        List<String> list = new ArrayList<>(subscribers.keySet());
        return ResponseEntity.ok(list);
    }

    /**
     * Retrieve all stored NBA games, with optional pagination (page, size).
     */
    @GetMapping(path = "/games/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "100") int size) {

        logger.info("Fetching all games with pagination page={}, size={}", page, size);

        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        // Simple pagination:
        int fromIndex = page * size;
        if (fromIndex >= allGames.size()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        int toIndex = Math.min(fromIndex + size, allGames.size());
        List<Game> pagedList = allGames.subList(fromIndex, toIndex);

        return ResponseEntity.ok(pagedList);
    }

    /**
     * Retrieve all games for a specific date.
     */
    @GetMapping(path = "/games/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getGamesByDate(@PathVariable("date") String dateStr) {
        logger.info("Fetching games for date: {}", dateStr);
        try {
            LocalDate.parse(dateStr); // validate format
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM-DD.");
        }

        List<Game> games = gamesByDate.getOrDefault(dateStr, Collections.emptyList());
        return ResponseEntity.ok(games);
    }


    /**
     * POST endpoint to fetch and store NBA scores for a given date, then notify subscribers.
     * This simulates the daily scheduler trigger.
     */
    @PostMapping(path = "/scores/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> fetchScores(@RequestBody FetchScoresRequest request) {
        logger.info("Received request to fetch NBA scores for date: {}", request.getDate());

        if (!StringUtils.hasText(request.getDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is required");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM-DD.");
        }

        // Fire-and-forget asynchronous fetch and notify
        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(date);
            } catch (Exception e) {
                logger.error("Error during async fetch/store/notify for date {}: {}", date, e.getMessage(), e);
            }
        });

        return ResponseEntity.ok(new MessageResponse("Scores fetching started"));
    }


    // ========== Private Helpers ==========

    /**
     * Fetch NBA scores from external API, store locally, and send notifications.
     * @param date the date to fetch scores for
     */
    private void fetchStoreAndNotify(LocalDate date) throws URISyntaxException {
        String dateStr = date.toString();
        String url = String.format(NBA_API_URL_TEMPLATE, dateStr, NBA_API_KEY);
        logger.info("Fetching NBA scores from external API: {}", url);

        URI uri = new URI(url);
        String rawJson;
        try {
            rawJson = restTemplate.getForObject(uri, String.class);
        } catch (Exception e) {
            logger.error("Failed to fetch NBA scores for {}: {}", dateStr, e.getMessage());
            return;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                logger.warn("Expected JSON array of games but got: {}", rootNode.getNodeType());
                return;
            }

            List<Game> gamesList = new ArrayList<>();
            for (JsonNode gameNode : rootNode) {
                Game game = parseGameFromJsonNode(gameNode);
                if (game != null) {
                    gamesList.add(game);
                }
            }

            gamesByDate.put(dateStr, gamesList);
            logger.info("Stored {} games for date {}", gamesList.size(), dateStr);

            sendNotificationEmails(dateStr, gamesList);

        } catch (Exception ex) {
            logger.error("Error parsing NBA scores JSON for date {}: {}", dateStr, ex.getMessage());
        }
    }

    /**
     * Parse a single game from the external API JSON node.
     * Minimal fields: date, homeTeam, awayTeam, homeScore, awayScore, status.
     */
    private Game parseGameFromJsonNode(JsonNode node) {
        try {
            String date = node.path("Day").asText(null);
            if (date == null) {
                date = node.path("DateTime").asText(null);
                if (date != null && date.length() >= 10) {
                    date = date.substring(0, 10);
                }
            }
            String homeTeam = node.path("HomeTeam").asText(null);
            String awayTeam = node.path("AwayTeam").asText(null);
            Integer homeScore = null;
            Integer awayScore = null;
            if (node.has("HomeTeamScore") && node.get("HomeTeamScore").isInt()) {
                homeScore = node.get("HomeTeamScore").asInt();
            }
            if (node.has("AwayTeamScore") && node.get("AwayTeamScore").isInt()) {
                awayScore = node.get("AwayTeamScore").asInt();
            }
            String status = node.path("Status").asText("Unknown");

            if (date == null || homeTeam == null || awayTeam == null) {
                logger.warn("Skipping incomplete game data: {}", node.toString());
                return null;
            }

            return new Game(date, homeTeam, awayTeam, homeScore, awayScore, status);
        } catch (Exception e) {
            logger.warn("Exception parsing game node: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Send email notifications to all subscribers with the daily scores summary.
     * This is a mock implementation.
     */
    private void sendNotificationEmails(String dateStr, List<Game> games) {
        if (subscribers.isEmpty()) {
            logger.info("No subscribers to notify for date {}", dateStr);
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("NBA Scores for ").append(dateStr).append(":\n\n");
        for (Game game : games) {
            summary.append(String.format("%s vs %s: %d - %d (%s)\n",
                    game.getAwayTeam(),
                    game.getHomeTeam(),
                    Optional.ofNullable(game.getAwayScore()).orElse(0),
                    Optional.ofNullable(game.getHomeScore()).orElse(0),
                    game.getStatus()));
        }

        // TODO: Replace this mock with real email sending logic.
        subscribers.keySet().forEach(email -> {
            logger.info("Sending email to {}:\n{}", email, summary.toString());
            // Fire-and-forget email sending could be done here
        });

        logger.info("Notifications sent to {} subscribers", subscribers.size());
    }


    // ========== Minimal error handler ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }
}
```