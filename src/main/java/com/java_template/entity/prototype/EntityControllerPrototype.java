```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("prototype")
@Slf4j
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    @PostConstruct
    public void init() {
        log.info("Prototype EntityController initialized");
    }

    /* -----------------------------------
       Subscription Endpoints
     ----------------------------------- */

    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT).trim();
        if (subscribers.containsKey(email)) {
            log.info("Subscription attempt for existing email: {}", email);
            return ResponseEntity.ok(new SubscriptionResponse("Email already subscribed", email));
        }
        subscribers.put(email, new Subscriber(email, Instant.now()));
        log.info("New subscriber added: {}", email);
        return ResponseEntity.ok(new SubscriptionResponse("Subscription successful", email));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        List<String> emails = new ArrayList<>(subscribers.keySet());
        log.info("Retrieving all subscribers, count: {}", emails.size());
        return ResponseEntity.ok(new SubscribersResponse(emails));
    }

    /* -----------------------------------
       Game Data Fetching and Storage
     ----------------------------------- */

    @PostMapping("/games/fetch")
    public ResponseEntity<GameFetchResponse> fetchGames(@Valid @RequestBody GameFetchRequest request) {
        String date = request.getDate();
        log.info("Received request to fetch games for date: {}", date);

        try {
            fetchAndStoreGamesAsync(date);
        } catch (Exception e) {
            log.error("Error starting async fetch for date {}: {}", date, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start data fetch");
        }

        return ResponseEntity.ok(new GameFetchResponse("Game data fetch triggered", date, null));
    }

    @Async
    public CompletableFuture<Void> fetchAndStoreGamesAsync(String date) {
        try {
            String url = String.format(EXTERNAL_API_URL_TEMPLATE, date, EXTERNAL_API_KEY);
            log.info("Calling external API: {}", url);

            String response = restTemplate.getForObject(new URI(url), String.class);
            JsonNode rootNode = objectMapper.readTree(response);

            if (!rootNode.isArray()) {
                log.error("Unexpected response format from external API (not an array)");
                return CompletableFuture.completedFuture(null);
            }

            List<Game> gamesList = new ArrayList<>();
            for (JsonNode gameNode : rootNode) {
                Game g = new Game();
                g.setGameId(gameNode.path("GameID").asText("unknown"));
                g.setDate(date);
                g.setHomeTeam(gameNode.path("HomeTeam").asText("unknown"));
                g.setAwayTeam(gameNode.path("AwayTeam").asText("unknown"));
                g.setHomeScore(gameNode.path("HomeTeamScore").asInt(-1));
                g.setAwayScore(gameNode.path("AwayTeamScore").asInt(-1));
                g.setStatus(gameNode.path("Status").asText("unknown"));
                gamesList.add(g);
            }

            gamesByDate.put(date, gamesList);
            log.info("Stored {} games for date {}", gamesList.size(), date);

            // Fire-and-forget email notification to subscribers
            CompletableFuture.runAsync(() -> sendEmailNotifications(date, gamesList)); // TODO: Implement real email sending

        } catch (URISyntaxException e) {
            log.error("Invalid URI for external API call: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching or processing external API response: {}", e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /* -----------------------------------
       Game Data Retrieval Endpoints
     ----------------------------------- */

    @GetMapping("/games/all")
    public ResponseEntity<GamesResponse> getAllGames(@RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                                     @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        log.info("Retrieving all games, page: {}, size: {}", page, size);

        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);

        int start = (page - 1) * size;
        int end = Math.min(start + size, allGames.size());
        if (start > allGames.size()) {
            start = end = 0;
        }

        List<Game> paged = allGames.subList(start, end);

        GamesResponse response = new GamesResponse(paged, page, size, allGames.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<GamesResponse> getGamesByDate(@PathVariable String date) {
        log.info("Retrieving games for date: {}", date);
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        GamesResponse response = new GamesResponse(games, 1, games.size(), games.size());
        return ResponseEntity.ok(response);
    }

    /* -----------------------------------
       Minimal Email Notification Mock
     ----------------------------------- */

    private void sendEmailNotifications(String date, List<Game> games) {
        log.info("Sending email notifications for date {}, to {} subscribers", date, subscribers.size());

        // TODO: Replace this with real email sending logic
        StringBuilder summary = new StringBuilder("NBA Scores for " + date + ":\n");
        for (Game g : games) {
            summary.append(String.format("%s vs %s: %d - %d (%s)\n",
                    g.getAwayTeam(), g.getHomeTeam(), g.getAwayScore(), g.getHomeScore(), g.getStatus()));
        }
        for (Subscriber s : subscribers.values()) {
            log.info("Email to {}:\n{}", s.getEmail(), summary);
        }
    }

    /* -----------------------------------
       Exception Handling
     ----------------------------------- */

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling error: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Unexpected error occurred");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /* -----------------------------------
       DTOs & Models
     ----------------------------------- */

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscriptionRequest {
        private String email;
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
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameFetchResponse {
        private String message;
        private String date;
        private Integer gamesCount; // null in async prototype response
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
```