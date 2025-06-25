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
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final String API_KEY = "test"; // TODO: replace with real API key or config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage mocks
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    // --- Models ---

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
        // Additional fields can be added here
    }

    @Data
    static class SubscribeRequest {
        private String email;
    }

    @Data
    static class FetchRequest {
        private String date; // YYYY-MM-DD
    }

    @Data
    static class NotificationRequest {
        private String date; // YYYY-MM-DD
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
        private String email;
        private String date;
        private Integer gamesCount;
        private Integer emailsSent;
    }

    // --- API Endpoints ---

    /**
     * Subscribe user with email
     */
    @PostMapping("/subscribe")
    public MessageResponse subscribe(@RequestBody SubscribeRequest request) {
        log.info("Subscribe request received for email={}", request.getEmail());

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be provided");
        }

        subscribers.put(request.getEmail().toLowerCase(Locale.ROOT), new Subscriber(request.getEmail()));
        log.info("Email {} added to subscribers", request.getEmail());

        return new MessageResponse("Subscription successful", request.getEmail(), null, null, null);
    }

    /**
     * Retrieve all subscribers
     */
    @GetMapping("/subscribers")
    public Collection<String> getSubscribers() {
        log.info("Retrieving all subscribers");
        return subscribers.keySet();
    }

    /**
     * Fetch NBA scores from external API for a given date and store locally
     */
    @PostMapping("/games/fetch")
    public MessageResponse fetchAndStoreScores(@RequestBody FetchRequest request) {
        String date = request.getDate();
        log.info("Fetch NBA scores request for date={}", date);

        if (date == null || date.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be provided");
        }

        String url = String.format(NBA_API_URL_TEMPLATE, date, API_KEY);
        try {
            String rawJson = restTemplate.getForObject(new URI(url), String.class);
            if (rawJson == null || rawJson.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from NBA API");
            }
            JsonNode rootNode = objectMapper.readTree(rawJson);

            List<Game> gamesForDate = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    Game game = parseGameFromJson(node, date);
                    gamesForDate.add(game);
                }
            } else {
                // Unexpected format, but try parse single object
                Game game = parseGameFromJson(rootNode, date);
                gamesForDate.add(game);
            }

            gamesByDate.put(date, gamesForDate);
            log.info("Stored {} games for date {}", gamesForDate.size(), date);

            return new MessageResponse("Scores fetched and stored successfully", null, date, gamesForDate.size(), null);

        } catch (Exception ex) {
            log.error("Error fetching or parsing NBA API data for date={}: {}", date, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch or parse NBA data");
        }
    }

    /**
     * Send daily NBA scores notification emails to all subscribers for a given date
     */
    @PostMapping("/notifications/send")
    public MessageResponse sendNotifications(@RequestBody NotificationRequest request) {
        String date = request.getDate();
        log.info("Send notifications request for date={}", date);

        if (date == null || date.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be provided");
        }

        List<Game> games = gamesByDate.get(date);
        if (games == null || games.isEmpty()) {
            log.warn("No games found for date {}, notifications not sent", date);
            return new MessageResponse("No games found for date, notifications not sent", null, date, 0, 0);
        }

        Collection<Subscriber> currentSubscribers = subscribers.values();
        if (currentSubscribers.isEmpty()) {
            log.info("No subscribers to notify");
            return new MessageResponse("No subscribers to notify", null, date, games.size(), 0);
        }

        // Fire-and-forget async notification sending
        CompletableFuture.runAsync(() -> {
            log.info("Sending notifications to {} subscribers for date {}", currentSubscribers.size(), date);
            // TODO: Replace with real email sending logic
            for (Subscriber sub : currentSubscribers) {
                // Simulate sending email
                logger.info("Sending email to {} with {} games summary for date {}", sub.getEmail(), games.size(), date);
            }
            log.info("Completed sending notifications for date {}", date);
        });

        return new MessageResponse("Notifications sending started", null, date, games.size(), currentSubscribers.size());
    }

    /**
     * Retrieve all stored games (optional pagination/filtering not implemented in prototype)
     */
    @GetMapping("/games/all")
    public List<Game> getAllGames() {
        log.info("Retrieving all stored games");
        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        return allGames;
    }

    /**
     * Retrieve all games for a specific date
     */
    @GetMapping("/games/{date}")
    public List<Game> getGamesByDate(@PathVariable String date) {
        log.info("Retrieving games for date {}", date);
        List<Game> games = gamesByDate.get(date);
        if (games == null) {
            log.warn("No games found for date {}", date);
            return Collections.emptyList();
        }
        return games;
    }

    // --- Helpers ---

    private Game parseGameFromJson(JsonNode node, String date) {
        // Parsing fields with fallback for missing values
        String gameId = node.hasNonNull("GameID") ? node.get("GameID").asText() : UUID.randomUUID().toString();
        String homeTeam = node.hasNonNull("HomeTeam") ? node.get("HomeTeam").asText() : "Unknown";
        String awayTeam = node.hasNonNull("AwayTeam") ? node.get("AwayTeam").asText() : "Unknown";
        Integer homeScore = node.hasNonNull("HomeTeamScore") ? node.get("HomeTeamScore").asInt() : null;
        Integer awayScore = node.hasNonNull("AwayTeamScore") ? node.get("AwayTeamScore").asInt() : null;

        return new Game(gameId, date, homeTeam, awayTeam, homeScore, awayScore);
    }

    // --- Minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getMessage());
        return Map.of(
                "error", ex.getReason(),
                "status", ex.getStatusCode().value()
        );
    }
}
```
