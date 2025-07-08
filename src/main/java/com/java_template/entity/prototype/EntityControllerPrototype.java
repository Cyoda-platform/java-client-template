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

import javax.validation.constraints.Email;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory "database" mocks
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    private static final String EXTERNAL_API_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test";

    // -----------------
    // DTO / Entity models
    // -----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        @Email
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Game {
        private String date;        // YYYY-MM-DD
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private Map<String, Object> additionalInfo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        private String date; // YYYY-MM-DD
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ApiResponse {
        private String status;
        private String message;
    }

    // -----------------
    // API Endpoints
    // -----------------

    /**
     * POST /prototype/fetch-scores
     * Fetches NBA scores for a given date from external API, stores them,
     * and asynchronously sends notifications to subscribers.
     */
    @PostMapping("/fetch-scores")
    public ResponseEntity<ApiResponse> fetchScores(@RequestBody FetchScoresRequest request) {
        if (!StringUtils.hasText(request.getDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date parameter is required");
        }

        log.info("Received request to fetch scores for date: {}", request.getDate());

        // Call external API synchronously to fetch data
        String url = String.format(EXTERNAL_API_TEMPLATE, request.getDate());

        JsonNode apiResponse;
        try {
            URI uri = new URI(url);
            String responseStr = restTemplate.getForObject(uri, String.class);
            apiResponse = objectMapper.readTree(responseStr);
        } catch (Exception e) {
            log.error("Failed to fetch or parse external NBA data", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve external NBA data");
        }

        // Parse and map the external JSON response into List<Game>
        List<Game> games = new ArrayList<>();
        if (apiResponse.isArray()) {
            for (JsonNode node : apiResponse) {
                try {
                    Game g = new Game();
                    g.setDate(request.getDate());
                    g.setHomeTeam(node.path("HomeTeam").asText(null));
                    g.setAwayTeam(node.path("AwayTeam").asText(null));
                    g.setHomeScore(node.path("HomeTeamScore").isInt() ? node.path("HomeTeamScore").asInt() : null);
                    g.setAwayScore(node.path("AwayTeamScore").isInt() ? node.path("AwayTeamScore").asInt() : null);

                    // Store other fields dynamically
                    Map<String, Object> additional = new HashMap<>();
                    node.fieldNames().forEachRemaining(field -> {
                        if (!Arrays.asList("HomeTeam", "AwayTeam", "HomeTeamScore", "AwayTeamScore").contains(field)) {
                            additional.put(field, node.get(field));
                        }
                    });
                    g.setAdditionalInfo(additional);
                    games.add(g);
                } catch (Exception e) {
                    log.warn("Skipping malformed game entry in external data", e);
                }
            }
        } else {
            log.warn("External API response is not an array for date {}", request.getDate());
        }

        // Overwrite existing data for the date
        gamesByDate.put(request.getDate(), games);
        log.info("Stored {} games for date {}", games.size(), request.getDate());

        // Fire-and-forget async notification sending
        sendNotificationsAsync(request.getDate(), games);

        return ResponseEntity.ok(new ApiResponse("success", "Scores fetched, stored, and notifications sent."));
    }

    @Async
    void sendNotificationsAsync(String date, List<Game> games) {
        // TODO Replace with actual email sending logic
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending notification emails for date {} to {} subscribers", date, subscribers.size());
                StringBuilder summary = new StringBuilder();
                summary.append("NBA Scores for ").append(date).append(":\n");
                for (Game g : games) {
                    summary.append(String.format("%s vs %s: %s - %s\n",
                            g.getHomeTeam(),
                            g.getAwayTeam(),
                            g.getHomeScore() != null ? g.getHomeScore() : "?",
                            g.getAwayScore() != null ? g.getAwayScore() : "?"));
                }

                for (Subscriber s : subscribers.values()) {
                    // TODO send email to s.getEmail() with summary.toString()
                    log.info("Pretending to send email to {}:\n{}", s.getEmail(), summary);
                }

                log.info("Notification emails sent.");
            } catch (Exception e) {
                log.error("Error during sending notification emails", e);
            }
        });
    }

    /**
     * POST /prototype/subscribe
     * Registers a new subscriber email.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse> subscribe(@RequestBody SubscribeRequest request) {
        if (!StringUtils.hasText(request.getEmail()) || !request.getEmail().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email is required");
        }

        subscribers.put(request.getEmail().toLowerCase(), new Subscriber(request.getEmail().toLowerCase(), Instant.now()));
        log.info("New subscriber added: {}", request.getEmail());

        return ResponseEntity.ok(new ApiResponse("success", "Email subscribed successfully."));
    }

    /**
     * GET /prototype/subscribers
     * Returns all subscribed emails.
     */
    @GetMapping("/subscribers")
    public ResponseEntity<Collection<String>> getSubscribers() {
        return ResponseEntity.ok(subscribers.keySet());
    }

    /**
     * GET /prototype/games/all
     * Returns all stored games, optionally paginated.
     */
    @GetMapping("/games/all")
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size) {

        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);

        int fromIndex = Math.min(page * size, allGames.size());
        int toIndex = Math.min(fromIndex + size, allGames.size());

        return ResponseEntity.ok(allGames.subList(fromIndex, toIndex));
    }

    /**
     * GET /prototype/games/{date}
     * Returns games for a specific date.
     */
    @GetMapping("/games/{date}")
    public ResponseEntity<List<Game>> getGamesByDate(@PathVariable String date) {
        if (!StringUtils.hasText(date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date parameter is required");
        }

        List<Game> games = gamesByDate.get(date);
        if (games == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        return ResponseEntity.ok(games);
    }

    // -----------------
    // Minimal error handling
    // -----------------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Error response: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
```