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

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.net.URI;
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
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribersResponse {
        private List<String> subscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchScoresRequest {
        // optional - if empty, use current date
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchScoresResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesResponse {
        private int page;
        private int size;
        private long totalGames;
        private List<Game> games;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesByDateResponse {
        private String date;
        private List<Game> games;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
        // TODO: add other relevant fields if needed
    }

    // --- API Endpoints ---

    /**
     * POST /subscribe - add subscriber
     */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        if (subscribers.containsKey(email)) {
            log.info("Subscription attempt for existing email: {}", email);
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", email));
        }
        subscribers.put(email, new Subscriber(email, Instant.now()));
        log.info("New subscriber added: {}", email);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubscribeResponse("Subscription successful", email));
    }

    /**
     * GET /subscribers - list all subscribers
     */
    @GetMapping("/subscribers")
    public SubscribersResponse getSubscribers() {
        log.info("Retrieving all subscribers, count={}", subscribers.size());
        return new SubscribersResponse(new ArrayList<>(subscribers.keySet()));
    }

    /**
     * POST /games/fetch - fetch scores from external API, store locally, send notifications
     */
    @PostMapping("/games/fetch")
    public ResponseEntity<FetchScoresResponse> fetchAndStoreScores(@RequestBody(required = false) FetchScoresRequest request) {
        String dateStr = null;
        if (request != null && StringUtils.hasText(request.getDate())) {
            dateStr = request.getDate();
        } else {
            dateStr = java.time.LocalDate.now().toString();
        }

        log.info("Fetching NBA scores for date: {}", dateStr);

        List<Game> fetchedGames;
        try {
            fetchedGames = fetchScoresFromExternalApi(dateStr);
        } catch (Exception ex) {
            log.error("Failed to fetch NBA scores from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch from external API");
        }

        // Store fetched games locally (overwrite for date)
        gamesByDate.put(dateStr, fetchedGames);
        log.info("Stored {} games for date {}", fetchedGames.size(), dateStr);

        // Fire and forget notification sending
        CompletableFuture.runAsync(() -> sendNotifications(dateStr, fetchedGames));
        // TODO: Implement proper async notification sending with retry, error handling

        return ResponseEntity.ok(new FetchScoresResponse("Scores fetched, stored and notifications sent", dateStr, fetchedGames.size()));
    }

    /**
     * GET /games/all - retrieves all stored NBA games with optional paging
     */
    @GetMapping("/games/all")
    public GamesResponse getAllGames(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving all games with pagination - page: {}, size: {}", page, size);

        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allGames.size());

        if (fromIndex >= allGames.size()) {
            return new GamesResponse(page, size, allGames.size(), Collections.emptyList());
        }

        List<Game> pageGames = allGames.subList(fromIndex, toIndex);

        return new GamesResponse(page, size, allGames.size(), pageGames);
    }

    /**
     * GET /games/{date} - get games by date
     */
    @GetMapping("/games/{date}")
    public GamesByDateResponse getGamesByDate(@PathVariable String date) {
        log.info("Retrieving games for date {}", date);
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return new GamesByDateResponse(date, games);
    }

    // --- Helper methods ---

    /**
     * Calls external API to fetch NBA scores for a specific date.
     * Parses JSON response into Game list.
     */
    private List<Game> fetchScoresFromExternalApi(String date) throws Exception {
        String url = String.format(EXTERNAL_API_URL_TEMPLATE, date, API_KEY);
        log.info("Calling external API: {}", url);

        String rawJson = restTemplate.getForObject(new URI(url), String.class);
        if (rawJson == null) {
            throw new IllegalStateException("Empty response from external API");
        }
        JsonNode rootNode = objectMapper.readTree(rawJson);

        List<Game> gameList = new ArrayList<>();
        if (rootNode.isArray()) {
            for (JsonNode gameNode : rootNode) {
                Game game = parseGameFromJsonNode(gameNode, date);
                gameList.add(game);
            }
        } else if (rootNode.isObject()) {
            // Sometimes API might return a single object
            gameList.add(parseGameFromJsonNode(rootNode, date));
        } else {
            log.warn("Unexpected JSON structure from external API");
        }

        return gameList;
    }

    private Game parseGameFromJsonNode(JsonNode node, String date) {
        String homeTeam = safeText(node, "HomeTeam");
        String awayTeam = safeText(node, "AwayTeam");
        Integer homeScore = safeInt(node, "HomeTeamScore");
        Integer awayScore = safeInt(node, "AwayTeamScore");
        String status = safeText(node, "Status");

        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, status);
    }

    private String safeText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && !child.isNull()) return child.asText();
        return null;
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

    /**
     * Simulates sending notifications to subscribers.
     * TODO: implement real email sending logic (e.g. via email service)
     */
    @Async
    public void sendNotifications(String date, List<Game> games) {
        log.info("Sending notifications for date {} to {} subscribers", date, subscribers.size());

        StringBuilder emailContent = new StringBuilder();
        emailContent.append("NBA Scores for ").append(date).append(":\n\n");
        for (Game game : games) {
            emailContent.append(String.format("%s vs %s: %s - %s (Status: %s)\n",
                    game.getHomeTeam(),
                    game.getAwayTeam(),
                    safeScoreString(game.getHomeScore()),
                    safeScoreString(game.getAwayScore()),
                    game.getStatus()));
        }

        // Mock sending email to all subscribers
        subscribers.values().forEach(sub -> {
            // TODO: Replace with real email sending integration
            log.info("Sending email to {}:\n{}", sub.getEmail(), emailContent.toString());
        });

        log.info("Notification sending complete");
    }

    private String safeScoreString(Integer score) {
        return score == null ? "N/A" : score.toString();
    }

    // --- Basic error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason() != null ? ex.getReason() : "Unexpected error");
        log.error("ResponseStatusException: {} - {}", error.get("error"), error.get("message"));
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "INTERNAL_SERVER_ERROR");
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Unexpected server error");
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```