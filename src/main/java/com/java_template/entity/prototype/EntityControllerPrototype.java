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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + API_KEY;

    //
    // --- API Endpoints ---
    //

    /**
     * Subscribe user by email.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());

        if (subscribers.containsKey(request.getEmail())) {
            logger.info("Email {} is already subscribed", request.getEmail());
            return ResponseEntity.ok(new SubscriptionResponse("Already subscribed", request.getEmail()));
        }

        Subscriber subscriber = new Subscriber(request.getEmail(), Instant.now());
        subscribers.put(request.getEmail(), subscriber);
        logger.info("Email {} subscribed successfully", request.getEmail());

        return ResponseEntity.ok(new SubscriptionResponse("Subscription successful", request.getEmail()));
    }

    /**
     * Get all subscriber emails.
     */
    @GetMapping("/subscribers")
    public ResponseEntity<List<String>> getSubscribers() {
        logger.info("Retrieving all subscribers, count: {}", subscribers.size());
        return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
    }

    /**
     * Retrieve all stored games with optional pagination.
     */
    @GetMapping("/games/all")
    public ResponseEntity<List<Game>> getAllGames(@RequestParam(defaultValue = "20") int limit,
                                                  @RequestParam(defaultValue = "0") int offset) {
        logger.info("Retrieving all games with limit {} and offset {}", limit, offset);

        List<Game> allGames = new ArrayList<>();
        for (List<Game> dailyGames : gamesByDate.values()) {
            allGames.addAll(dailyGames);
        }
        allGames.sort(Comparator.comparing(Game::getDate).thenComparing(Game::getGameId));

        int fromIndex = Math.min(offset, allGames.size());
        int toIndex = Math.min(offset + limit, allGames.size());

        return ResponseEntity.ok(allGames.subList(fromIndex, toIndex));
    }

    /**
     * Retrieve games for a specific date.
     */
    @GetMapping("/games/{date}")
    public ResponseEntity<List<Game>> getGamesByDate(@PathVariable String date) {
        logger.info("Retrieving games for date {}", date);
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(games);
    }

    /**
     * Manually trigger fetching NBA scores, storing them and sending notifications.
     */
    @PostMapping("/games/fetch")
    public ResponseEntity<FetchResponse> fetchAndNotify(@RequestBody(required = false) FetchRequest request) {
        String dateParam = (request != null && request.getDate() != null) ? request.getDate() : LocalDate.now().toString();
        logger.info("Manual fetch and notify triggered for date {}", dateParam);

        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(dateParam);
            } catch (Exception e) {
                logger.error("Error during fetchStoreAndNotify async process", e);
                // TODO: add error notification or retry logic if needed
            }
        });

        return ResponseEntity.ok(new FetchResponse("Scores fetching and notification started", dateParam, -1));
    }

    //
    // --- Internal / Helper methods ---
    //

    /**
     * Fetch from external API, store data locally and send notifications.
     */
    @Async // This annotation generally requires a @EnableAsync config elsewhere, TODO: ensure async config is enabled
    public void fetchStoreAndNotify(String date) {
        logger.info("Starting fetchStoreAndNotify for date {}", date);

        String url = String.format(EXTERNAL_API_URL, date);
        JsonNode rootNode;
        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                logger.error("Empty response from external API for date {}", date);
                return;
            }
            rootNode = objectMapper.readTree(jsonResponse);
        } catch (Exception e) {
            logger.error("Failed to fetch or parse external API response for date {}: {}", date, e.getMessage());
            return;
        }

        if (!rootNode.isArray()) {
            logger.error("Unexpected JSON format: expected array but got {}", rootNode.getNodeType());
            return;
        }

        List<Game> gamesList = new ArrayList<>();
        for (JsonNode node : rootNode) {
            try {
                Game game = new Game(
                        node.path("GameID").asText(""),
                        node.path("Day").asText(date),
                        node.path("HomeTeam").asText(""),
                        node.path("AwayTeam").asText(""),
                        node.path("HomeTeamScore").asInt(0),
                        node.path("AwayTeamScore").asInt(0)
                );
                gamesList.add(game);
            } catch (Exception e) {
                logger.warn("Skipping a game due to parse error: {}", e.getMessage());
            }
        }

        gamesByDate.put(date, gamesList);
        logger.info("Stored {} games for date {}", gamesList.size(), date);

        sendNotifications(date, gamesList);
    }

    /**
     * Mock method to send email notifications to all subscribers with daily game summaries.
     * TODO: Replace with real email service integration.
     */
    private void sendNotifications(String date, List<Game> gamesList) {
        logger.info("Sending notifications to {} subscribers for date {}", subscribers.size(), date);

        if (subscribers.isEmpty()) {
            logger.info("No subscribers found, skipping notifications");
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("NBA Scores for ").append(date).append(":\n");
        for (Game g : gamesList) {
            summary.append(String.format("%s vs %s : %d - %d\n",
                    g.getHomeTeam(), g.getAwayTeam(), g.getHomeScore(), g.getAwayScore()));
        }

        subscribers.values().forEach(subscriber -> {
            // TODO: Replace this log with actual email sending logic
            logger.info("Sending email to {}:\n{}", subscriber.getEmail(), summary);
        });
    }

    //
    // --- Exception Handling ---
    //

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    //
    // --- DTOs and Models ---
    //

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class SubscriptionResponse {
        private String message;
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @AllArgsConstructor
    public static class Game {
        private String gameId;
        private String date; // YYYY-MM-DD
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
    }

    @Data
    @NoArgsConstructor
    public static class FetchRequest {
        private String date; // optional, YYYY-MM-DD
    }

    @Data
    @AllArgsConstructor
    public static class FetchResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
```