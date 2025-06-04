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

import javax.validation.constraints.Email;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Subscribers storage: email -> Subscriber
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    // Pending confirmation tokens: token -> email
    private final Map<String, String> subscriptionConfirmations = new ConcurrentHashMap<>();
    private final Map<String, String> unsubscriptionConfirmations = new ConcurrentHashMap<>();

    // Stored NBA games by date (YYYY-MM-DD) -> List<Game>
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    // Scheduler config prototype
    private SchedulerConfig schedulerConfig = new SchedulerConfig("0 0 18 * * ?", true);

    // === API ENDPOINTS ===

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody SubscribeRequest request) {
        log.info("Received subscription request for email '{}'", request.getEmail());
        if (!isValidEmail(request.getEmail())) {
            log.error("Invalid email format: {}", request.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
        }

        if (subscribers.containsKey(request.getEmail())) {
            log.error("Email '{}' already subscribed", request.getEmail());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }

        // Generate confirmation token
        String token = UUID.randomUUID().toString();
        subscriptionConfirmations.put(token, request.getEmail());

        // TODO: Send confirmation email with token asynchronously
        CompletableFuture.runAsync(() -> {
            log.info("Sending subscription confirmation email to {} with token {}", request.getEmail(), token);
            // TODO: integrate real email sending here
        });

        Map<String, String> resp = Map.of("message", "Subscription request received. Please confirm via email.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/subscribe/confirm")
    public ResponseEntity<Map<String, String>> confirmSubscription(@RequestBody ConfirmTokenRequest request) {
        log.info("Confirm subscription with token {}", request.getToken());
        String email = subscriptionConfirmations.remove(request.getToken());
        if (email == null) {
            log.error("Invalid or expired subscription confirmation token: {}", request.getToken());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        subscribers.put(email, new Subscriber(email, Instant.now()));
        log.info("Subscription confirmed for email {}", email);
        Map<String, String> resp = Map.of("message", "Subscription confirmed.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, String>> unsubscribe(@RequestBody UnsubscribeRequest request) {
        log.info("Received unsubscribe request for email '{}'", request.getEmail());
        if (!subscribers.containsKey(request.getEmail())) {
            log.error("Email '{}' not subscribed", request.getEmail());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not subscribed");
        }

        String token = UUID.randomUUID().toString();
        unsubscriptionConfirmations.put(token, request.getEmail());

        // TODO: Send unsubscribe confirmation email with token asynchronously
        CompletableFuture.runAsync(() -> {
            log.info("Sending unsubscribe confirmation email to {} with token {}", request.getEmail(), token);
            // TODO: integrate real email sending here
        });

        Map<String, String> resp = Map.of("message", "Unsubscribe request received. Please confirm via email.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/unsubscribe/confirm")
    public ResponseEntity<Map<String, String>> confirmUnsubscription(@RequestBody ConfirmTokenRequest request) {
        log.info("Confirm unsubscription with token {}", request.getToken());
        String email = unsubscriptionConfirmations.remove(request.getToken());
        if (email == null) {
            log.error("Invalid or expired unsubscription confirmation token: {}", request.getToken());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        subscribers.remove(email);
        log.info("Unsubscription confirmed for email {}", email);

        // TODO: Send unsubscription confirmed email asynchronously
        CompletableFuture.runAsync(() -> {
            log.info("Sending unsubscription confirmed email to {}", email);
            // TODO: integrate real email sending here
        });

        Map<String, String> resp = Map.of("message", "Unsubscription confirmed.");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersPageResponse> getSubscribers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving subscribers page {} size {}", page, size);

        List<String> allSubscribers = new ArrayList<>(subscribers.keySet());
        int total = allSubscribers.size();

        int fromIndex = page * size;
        if (fromIndex >= total) {
            return ResponseEntity.ok(new SubscribersPageResponse(page, size, (total + size - 1) / size, total, List.of()));
        }
        int toIndex = Math.min(fromIndex + size, total);
        List<String> pageContent = allSubscribers.subList(fromIndex, toIndex);

        SubscribersPageResponse resp = new SubscribersPageResponse(page, size, (total + size - 1) / size, total, pageContent);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<Map<String, String>> fetchAndStoreScores(@RequestBody FetchScoresRequest request) {
        log.info("Fetching NBA scores for date {}", request.getDate());
        // Validate date format loosely (YYYY-MM-DD)
        if (!StringUtils.hasLength(request.getDate()) || !request.getDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
            log.error("Invalid date format: {}", request.getDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }

        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(request.getDate());
            } catch (Exception e) {
                log.error("Error during fetch/store/notify for date {}: {}", request.getDate(), e.getMessage(), e);
            }
        });

        Map<String, String> resp = Map.of("message", "Scores fetch started for " + request.getDate() + ".");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<List<Game>> getGamesByDate(@PathVariable String date) {
        log.info("Retrieving games for date {}", date);
        // Validate date format loosely
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            log.error("Invalid date format: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(games);
    }

    @PostMapping("/scheduler/config")
    public ResponseEntity<Map<String, String>> updateSchedulerConfig(@RequestBody SchedulerConfigRequest request) {
        log.info("Updating scheduler config: cron='{}', enabled={}", request.getCronExpression(), request.isEnabled());
        if (!StringUtils.hasLength(request.getCronExpression())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cronExpression is required");
        }
        schedulerConfig = new SchedulerConfig(request.getCronExpression(), request.isEnabled());
        // TODO: update actual scheduler config if implemented
        Map<String, String> resp = Map.of("message", "Scheduler updated.");
        return ResponseEntity.ok(resp);
    }

    // === Helper methods ===

    private boolean isValidEmail(String email) {
        // Basic email format check
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    /**
     * Fetches NBA game scores from external API for given date,
     * stores them, and sends notification emails to confirmed subscribers.
     */
    private void fetchStoreAndNotify(String date) throws URISyntaxException {
        final String apiKey = "test"; // TODO: replace with config or secure storage
        String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date + "?key=" + apiKey;

        log.info("Calling external NBA API: {}", url);
        URI uri = new URI(url);

        String rawJson = restTemplate.getForObject(uri, String.class);
        if (rawJson == null) {
            log.error("Empty response from NBA API for date {}", date);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.isArray()) {
                log.warn("NBA API response is not an array for date {}: {}", date, root.toString());
                return;
            }

            List<Game> games = new ArrayList<>();
            for (JsonNode node : root) {
                Game game = new Game();
                game.setGameId(node.path("GameID").asText(null));
                game.setDate(date);
                game.setHomeTeam(node.path("HomeTeam").asText(null));
                game.setAwayTeam(node.path("AwayTeam").asText(null));
                game.setHomeScore(node.path("HomeTeamScore").asInt(0));
                game.setAwayScore(node.path("AwayTeamScore").asInt(0));
                game.setStatus(node.path("Status").asText(null));
                games.add(game);
            }
            gamesByDate.put(date, games);
            log.info("Stored {} games for date {}", games.size(), date);

            // Notify subscribers asynchronously
            CompletableFuture.runAsync(() -> {
                log.info("Sending notifications to {} subscribers", subscribers.size());
                for (Subscriber sub : subscribers.values()) {
                    // TODO: send actual HTML formatted email with daily summary
                    logger.info("Sending daily NBA scores email to {}", sub.getEmail());
                }
            });

        } catch (Exception e) {
            log.error("Error parsing NBA API response: {}", e.getMessage(), e);
        }
    }

    // === Exception Handlers ===

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    // === Data Classes ===

    @Data
    public static class SubscribeRequest {
        @Email
        private String email;
    }

    @Data
    public static class ConfirmTokenRequest {
        private String token;
    }

    @Data
    public static class UnsubscribeRequest {
        @Email
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
    @NoArgsConstructor
    public static class Game {
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class SubscribersPageResponse {
        private int page;
        private int size;
        private int totalPages;
        private int totalSubscribers;
        private List<String> subscribers;
    }

    @Data
    public static class FetchScoresRequest {
        private String date;
    }

    @Data
    public static class SchedulerConfigRequest {
        private String cronExpression;
        private boolean enabled;
    }

    @Data
    @AllArgsConstructor
    public static class SchedulerConfig {
        private String cronExpression;
        private boolean enabled;
    }
}
```
