```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + EXTERNAL_API_KEY;

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<LocalDate, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // --- DTOs ---

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Subscriber {
        private String email;
        private LocalDate subscribedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Game {
        private LocalDate date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        // Additional fields can be added as needed
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        private String date;  // YYYY-MM-DD
    }

    @Data
    @AllArgsConstructor
    public static class FetchScoresResponse {
        private String date;
        private int gamesFetched;
        private int subscribersNotified;
    }

    // --- Endpoints ---

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody SubscribeRequest request) {
        logger.info("Subscribe request received for email: {}", request.getEmail());
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be blank");
        }
        if (subscribers.containsKey(request.getEmail().toLowerCase(Locale.ROOT))) {
            logger.info("Email {} is already subscribed", request.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already subscribed");
        }
        Subscriber sub = new Subscriber(request.getEmail().toLowerCase(Locale.ROOT), LocalDate.now());
        subscribers.put(sub.getEmail(), sub);
        logger.info("Subscribed new email: {}", sub.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/subscribers")
    public List<String> getSubscribers() {
        logger.info("Fetching all subscriber emails");
        return new ArrayList<>(subscribers.keySet());
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody FetchScoresRequest request) {
        logger.info("Fetch scores request for date: {}", request.getDate());
        LocalDate requestedDate;
        try {
            requestedDate = LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: {}", request.getDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        CompletableFuture.runAsync(() -> fetchAndNotify(requestedDate)); // Fire-and-forget
        // Return immediately with processing status
        return ResponseEntity.ok(new FetchScoresResponse(request.getDate(), -1, subscribers.size()));
    }

    @GetMapping("/games/all")
    public List<Game> getAllGames() {
        logger.info("Fetching all games stored");
        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        return allGames;
    }

    @GetMapping("/games/{date}")
    public List<Game> getGamesByDate(@PathVariable String date) {
        logger.info("Fetching games for date: {}", date);
        LocalDate queryDate;
        try {
            queryDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format in path: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        return gamesByDate.getOrDefault(queryDate, Collections.emptyList());
    }

    // --- Internal Logic ---

    private void fetchAndNotify(LocalDate date) {
        logger.info("Starting fetch and notify for date: {}", date);
        try {
            String url = String.format(EXTERNAL_API_URL_TEMPLATE, date.toString());
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                logger.error("Empty response from external API for date: {}", date);
                return;
            }
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (!root.isArray()) {
                logger.error("Unexpected JSON structure received from external API for date: {}", date);
                return;
            }
            List<Game> fetchedGames = new ArrayList<>();
            for (JsonNode gameNode : root) {
                // Extract fields with null checks and fallback values
                String homeTeam = safeGetText(gameNode, "HomeTeam");
                String awayTeam = safeGetText(gameNode, "AwayTeam");
                Integer homeScore = safeGetInt(gameNode, "HomeTeamScore");
                Integer awayScore = safeGetInt(gameNode, "AwayTeamScore");

                if (homeTeam == null || awayTeam == null) {
                    logger.warn("Skipping game with incomplete team info: {}", gameNode.toString());
                    continue;
                }

                Game game = new Game(date, homeTeam, awayTeam, homeScore, awayScore);
                fetchedGames.add(game);
            }
            // Store fetched games (replace existing for the date)
            gamesByDate.put(date, fetchedGames);
            logger.info("Stored {} games for date {}", fetchedGames.size(), date);

            // Send notifications (mocked)
            sendEmailNotifications(date, fetchedGames);

        } catch (Exception ex) {
            logger.error("Error during fetch and notify process for date {}: {}", date, ex.getMessage(), ex);
        }
    }

    private String safeGetText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Integer safeGetInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull() && f.isInt()) ? f.asInt() : null;
    }

    private void sendEmailNotifications(LocalDate date, List<Game> games) {
        logger.info("Preparing to send email notifications to {} subscribers for date {}", subscribers.size(), date);
        // TODO: Replace this mock with real async email sending logic
        subscribers.keySet().forEach(email -> {
            logger.info("Sending email to {} with {} games summary for {}", email, games.size(), date);
            // Simulate sending email - fire and forget
            CompletableFuture.runAsync(() -> {
                // TODO: Implement real email sending here
                try {
                    Thread.sleep(50); // simulate delay
                } catch (InterruptedException ignored) {
                }
                logger.info("Email sent to {}", email);
            });
        });
    }

    // --- Minimal Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```