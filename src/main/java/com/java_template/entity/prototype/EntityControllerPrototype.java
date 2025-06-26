```java
package com.java_template.entity;

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
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private static final String NBA_API_KEY = "test"; // TODO: replace with real secure config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage mocks
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    // Key: date (yyyy-MM-dd), Value: list of games
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    // --- Models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private OffsetDateTime subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Game {
        private String date; // YYYY-MM-DD
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
        // TODO: Add other relevant fields if needed
    }

    @Data
    @AllArgsConstructor
    static class FetchScoresRequest {
        @NotBlank
        private String date; // YYYY-MM-DD
    }

    @Data
    @AllArgsConstructor
    static class FetchScoresResponse {
        private String message;
    }

    // --- API Endpoints ---

    /**
     * POST /subscribe
     * Add subscriber email to notification list.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        log.info("Subscription request received for email: {}", email);

        if (subscribers.containsKey(email)) {
            log.info("Email {} already subscribed", email);
            return ResponseEntity.ok(new SubscribeResponse("Email already subscribed", email));
        }

        Subscriber subscriber = new Subscriber(email, OffsetDateTime.now());
        subscribers.put(email, subscriber);

        log.info("Email {} subscribed successfully", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    /**
     * POST /scores/fetch
     * Fetch NBA scores for a given date from external API, store locally, send notifications.
     */
    @PostMapping("/scores/fetch")
    public ResponseEntity<FetchScoresResponse> fetchScores(@Valid @RequestBody FetchScoresRequest request) {
        String date = request.getDate();
        log.info("Fetch scores request received for date: {}", date);

        // Validate date format (basic)
        try {
            LocalDate.parse(date);
        } catch (Exception ex) {
            log.error("Invalid date format: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        CompletableFuture.runAsync(() -> fetchStoreAndNotify(date));

        return ResponseEntity.ok(new FetchScoresResponse("Scores fetching started for date " + date));
    }

    /**
     * GET /subscribers
     * Retrieve all subscribed emails.
     */
    @GetMapping("/subscribers")
    public ResponseEntity<List<String>> getAllSubscribers() {
        log.info("Retrieving all subscribers, count={}", subscribers.size());
        List<String> emails = new ArrayList<>(subscribers.keySet());
        return ResponseEntity.ok(emails);
    }

    /**
     * GET /games/all?page={page}&size={size}
     * Retrieve all stored NBA games with pagination.
     */
    @GetMapping("/games/all")
    public ResponseEntity<Map<String, Object>> getAllGames(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("Retrieving all games, page: {}, size: {}", page, size);

        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);

        int totalGames = allGames.size();
        int totalPages = (int) Math.ceil((double) totalGames / size);

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalGames);

        List<Game> pageContent = fromIndex < toIndex ? allGames.subList(fromIndex, toIndex) : Collections.emptyList();

        Map<String, Object> response = new HashMap<>();
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", totalPages);
        response.put("totalGames", totalGames);
        response.put("games", pageContent);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /games/{date}
     * Retrieve all games for a specific date.
     */
    @GetMapping("/games/{date}")
    public ResponseEntity<Map<String, Object>> getGamesByDate(@PathVariable String date) {
        log.info("Retrieving games for date: {}", date);
        if (!StringUtils.hasText(date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date parameter is required");
        }
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());

        Map<String, Object> response = new HashMap<>();
        response.put("date", date);
        response.put("games", games);

        return ResponseEntity.ok(response);
    }

    // --- Internal async logic ---

    @Async
    void fetchStoreAndNotify(String date) {
        log.info("Starting fetchStoreAndNotify for date {}", date);
        try {
            String url = String.format(NBA_API_URL_TEMPLATE, date, NBA_API_KEY);
            log.info("Fetching external NBA scores from URL: {}", url);

            URI uri = new URI(url);
            String rawJson = restTemplate.getForObject(uri, String.class);

            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                log.warn("Unexpected JSON structure from NBA API: not an array");
                return;
            }

            List<Game> fetchedGames = new ArrayList<>();
            for (JsonNode gameNode : rootNode) {
                Game game = new Game();
                game.setDate(date);
                game.setHomeTeam(getTextSafe(gameNode, "HomeTeam"));
                game.setAwayTeam(getTextSafe(gameNode, "AwayTeam"));
                game.setHomeScore(getIntSafe(gameNode, "HomeTeamScore"));
                game.setAwayScore(getIntSafe(gameNode, "AwayTeamScore"));
                game.setStatus(getTextSafe(gameNode, "Status"));

                fetchedGames.add(game);
            }

            gamesByDate.put(date, fetchedGames);
            log.info("Stored {} games for date {}", fetchedGames.size(), date);

            // Send notifications (fire-and-forget)
            CompletableFuture.runAsync(() -> sendEmailNotifications(date, fetchedGames));
        } catch (Exception e) {
            log.error("Error during fetchStoreAndNotify for date {}: {}", date, e.toString());
        }
    }

    private String getTextSafe(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private Integer getIntSafe(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        return (child != null && child.isInt()) ? child.asInt() : null;
    }

    // TODO: Replace with real email sending logic
    private void sendEmailNotifications(String date, List<Game> games) {
        log.info("Sending email notifications to {} subscribers for date {}", subscribers.size(), date);
        String summary = buildSummaryMessage(date, games);

        for (Subscriber subscriber : subscribers.values()) {
            // TODO: Implement real email sending here
            log.info("Sending email to {}: \n{}", subscriber.getEmail(), summary);
        }
    }

    private String buildSummaryMessage(String date, List<Game> games) {
        StringBuilder sb = new StringBuilder();
        sb.append("NBA Scores for ").append(date).append(":\n\n");
        for (Game g : games) {
            sb.append(String.format("%s vs %s: %d - %d [%s]\n",
                    g.getHomeTeam(), g.getAwayTeam(),
                    Optional.ofNullable(g.getHomeScore()).orElse(0),
                    Optional.ofNullable(g.getAwayScore()).orElse(0),
                    Optional.ofNullable(g.getStatus()).orElse("N/A")));
        }
        return sb.toString();
    }

    // --- Basic error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handling ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        logger.error("Unhandled exception: ", ex);
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Initialization for demo purposes ---
    @PostConstruct
    public void initDemoData() {
        // Add a demo subscriber
        subscribers.put("demo@example.com", new Subscriber("demo@example.com", OffsetDateTime.now()));
    }
}
```