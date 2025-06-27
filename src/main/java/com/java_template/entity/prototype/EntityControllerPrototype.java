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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    // =======================
    // === API: POST /fetch-scores
    // =======================
    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody FetchScoresRequest request) {
        log.info("Received fetch-scores request for date={}", request.getDate());
        if (!StringUtils.hasText(request.getDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date parameter is required");
        }

        // Validate date format YYYY-MM-DD (basic)
        if (!request.getDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }

        // Fire and forget fetch + store + notify
        CompletableFuture.runAsync(() -> fetchStoreNotify(request.getDate()));

        return ResponseEntity.ok(new FetchScoresResponse("success", request.getDate(), -1));
        // gamesCount unknown immediately - will update after fetch
    }

    @Async
    protected void fetchStoreNotify(String date) {
        log.info("Starting fetchStoreNotify for date={}", date);
        try {
            String url = String.format(EXTERNAL_API_TEMPLATE, date, API_KEY);
            log.info("Fetching external API URL: {}", url);

            URI uri = new URI(url);
            String rawJson = restTemplate.getForObject(uri, String.class);

            if (rawJson == null || rawJson.isBlank()) {
                log.error("Empty response from external API for date {}", date);
                return;
            }

            JsonNode rootNode = objectMapper.readTree(rawJson);

            // Parse games from JSON - assuming array of game objects
            List<Game> games = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode gameNode : rootNode) {
                    Game game = parseGameFromJson(gameNode);
                    games.add(game);
                }
            } else {
                log.warn("Unexpected JSON structure from external API for date {}", date);
            }

            if (!games.isEmpty()) {
                gamesByDate.put(date, games);
                log.info("Saved {} games for date {}", games.size(), date);
            } else {
                gamesByDate.put(date, Collections.emptyList());
                log.info("No games found for date {}", date);
            }

            // Send notifications
            sendNotifications(date, games);

        } catch (Exception e) {
            log.error("Error during fetchStoreNotify for date " + date, e);
        }
    }

    private Game parseGameFromJson(JsonNode node) {
        // Extract fields with fallbacks
        String homeTeam = node.path("HomeTeam").asText("");
        String awayTeam = node.path("AwayTeam").asText("");
        int homeScore = node.path("HomeTeamScore").asInt(-1);
        int awayScore = node.path("AwayTeamScore").asInt(-1);
        String dateStr = node.path("Day").asText(""); // ISO date string

        // TODO: Map additional fields as needed

        return new Game(dateStr, homeTeam, awayTeam, homeScore, awayScore);
    }

    private void sendNotifications(String date, List<Game> games) {
        log.info("Sending notifications to {} subscribers for date {}", subscribers.size(), date);

        StringBuilder summary = new StringBuilder();
        summary.append("NBA Scores for ").append(date).append(":\n");
        if (games.isEmpty()) {
            summary.append("No games played on this day.");
        } else {
            for (Game g : games) {
                summary.append(String.format("%s %d - %d %s\n",
                        g.getHomeTeam(), g.getHomeScore(), g.getAwayScore(), g.getAwayTeam()));
            }
        }

        for (Subscriber sub : subscribers.values()) {
            // TODO: Integrate real email service here
            log.info("Sending email to {} with summary:\n{}", sub.getEmail(), summary);
        }
    }

    // =======================
    // === API: POST /subscribe
    // =======================
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody SubscribeRequest request) {
        log.info("Subscription request received for email={}", request.getEmail());

        if (!StringUtils.hasText(request.getEmail()) || !request.getEmail().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email is required");
        }

        // Enforce unique subscription
        subscribers.putIfAbsent(request.getEmail().toLowerCase(Locale.ROOT), new Subscriber(request.getEmail()));

        log.info("Email {} subscribed successfully", request.getEmail());
        return ResponseEntity.ok(new SubscribeResponse("subscribed", request.getEmail()));
    }

    // =======================
    // === API: GET /subscribers
    // =======================
    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        log.info("Retrieving all subscribers, count={}", subscribers.size());
        List<String> emails = new ArrayList<>(subscribers.keySet());
        return ResponseEntity.ok(new SubscribersResponse(emails));
    }

    // =======================
    // === API: GET /games/all
    // =======================
    @GetMapping("/games/all")
    public ResponseEntity<GamesPageResponse> getAllGames(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving all games with pagination page={}, size={}", page, size);

        // Flatten all games across dates
        List<Game> allGames = new ArrayList<>();
        for (List<Game> games : gamesByDate.values()) {
            allGames.addAll(games);
        }

        int total = allGames.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);

        List<Game> pageContent = allGames.subList(fromIndex, toIndex);
        int totalPages = (total + size - 1) / size;

        GamesPageResponse response = new GamesPageResponse(page, size, totalPages, pageContent);
        return ResponseEntity.ok(response);
    }

    // =======================
    // === API: GET /games/{date}
    // =======================
    @GetMapping("/games/{date}")
    public ResponseEntity<GamesByDateResponse> getGamesByDate(@PathVariable String date) {
        log.info("Retrieving games for date={}", date);

        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }

        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(new GamesByDateResponse(date, games));
    }

    // =======================
    // === Error Handling
    // =======================
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", ex.getStatusCode().toString());
        logger.error("Handled error: {}, status: {}", ex.getReason(), ex.getStatusCode());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ===============
    // === DTOs & Entities
    // ===============

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresResponse {
        private String status;
        private String fetchedDate;
        private int gamesCount; // -1 if unknown immediately
    }

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
        private String status;
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
    static class GamesPageResponse {
        private int page;
        private int size;
        private int totalPages;
        private List<Game> games;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GamesByDateResponse {
        private String date;
        private List<Game> games;
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
        private String date;
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
    }
}
```