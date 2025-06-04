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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, String> subscriptionConfirmations = new ConcurrentHashMap<>();
    private final Map<String, String> unsubscriptionConfirmations = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();
    private SchedulerConfig schedulerConfig = new SchedulerConfig("0 0 18 * * ?", true);

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        log.info("Received subscription request for email '{}'", request.getEmail());
        if (subscribers.containsKey(request.getEmail())) {
            log.error("Email '{}' already subscribed", request.getEmail());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }
        String token = UUID.randomUUID().toString();
        subscriptionConfirmations.put(token, request.getEmail());
        CompletableFuture.runAsync(() -> {
            log.info("Sending subscription confirmation email to {} with token {}", request.getEmail(), token);
            // TODO: integrate real email sending here
        });
        return ResponseEntity.ok(Map.of("message", "Subscription request received. Please confirm via email."));
    }

    @PostMapping("/subscribe/confirm")
    public ResponseEntity<Map<String, String>> confirmSubscription(@RequestBody @Valid ConfirmTokenRequest request) {
        log.info("Confirm subscription with token {}", request.getToken());
        String email = subscriptionConfirmations.remove(request.getToken());
        if (email == null) {
            log.error("Invalid or expired subscription confirmation token: {}", request.getToken());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        subscribers.put(email, new Subscriber(email, Instant.now()));
        log.info("Subscription confirmed for email {}", email);
        return ResponseEntity.ok(Map.of("message", "Subscription confirmed."));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, String>> unsubscribe(@RequestBody @Valid UnsubscribeRequest request) {
        log.info("Received unsubscribe request for email '{}'", request.getEmail());
        if (!subscribers.containsKey(request.getEmail())) {
            log.error("Email '{}' not subscribed", request.getEmail());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not subscribed");
        }
        String token = UUID.randomUUID().toString();
        unsubscriptionConfirmations.put(token, request.getEmail());
        CompletableFuture.runAsync(() -> {
            log.info("Sending unsubscribe confirmation email to {} with token {}", request.getEmail(), token);
            // TODO: integrate real email sending here
        });
        return ResponseEntity.ok(Map.of("message", "Unsubscribe request received. Please confirm via email."));
    }

    @PostMapping("/unsubscribe/confirm")
    public ResponseEntity<Map<String, String>> confirmUnsubscription(@RequestBody @Valid ConfirmTokenRequest request) {
        log.info("Confirm unsubscription with token {}", request.getToken());
        String email = unsubscriptionConfirmations.remove(request.getToken());
        if (email == null) {
            log.error("Invalid or expired unsubscription confirmation token: {}", request.getToken());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        subscribers.remove(email);
        log.info("Unsubscription confirmed for email {}", email);
        CompletableFuture.runAsync(() -> {
            log.info("Sending unsubscription confirmed email to {}", email);
            // TODO: integrate real email sending here
        });
        return ResponseEntity.ok(Map.of("message", "Unsubscription confirmed."));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersPageResponse> getSubscribers(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        log.info("Retrieving subscribers page {} size {}", page, size);
        List<String> allSubscribers = new ArrayList<>(subscribers.keySet());
        int total = allSubscribers.size();
        int fromIndex = page * size;
        if (fromIndex >= total) {
            return ResponseEntity.ok(new SubscribersPageResponse(page, size, (total + size - 1) / size, total, List.of()));
        }
        int toIndex = Math.min(fromIndex + size, total);
        List<String> pageContent = allSubscribers.subList(fromIndex, toIndex);
        return ResponseEntity.ok(new SubscribersPageResponse(page, size, (total + size - 1) / size, total, pageContent));
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<Map<String, String>> fetchAndStoreScores(@RequestBody @Valid FetchScoresRequest request) {
        log.info("Fetching NBA scores for date {}", request.getDate());
        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(request.getDate());
            } catch (Exception e) {
                log.error("Error during fetch/store/notify for date {}: {}", request.getDate(), e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(Map.of("message", "Scores fetch started for " + request.getDate() + "."));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<List<Game>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        log.info("Retrieving games for date {}", date);
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(games);
    }

    @PostMapping("/scheduler/config")
    public ResponseEntity<Map<String, String>> updateSchedulerConfig(@RequestBody @Valid SchedulerConfigRequest request) {
        log.info("Updating scheduler config: cron='{}', enabled={}", request.getCronExpression(), request.isEnabled());
        schedulerConfig = new SchedulerConfig(request.getCronExpression(), request.isEnabled());
        // TODO: update actual scheduler config if implemented
        return ResponseEntity.ok(Map.of("message", "Scheduler updated."));
    }

    private void fetchStoreAndNotify(String date) throws URISyntaxException {
        String apiKey = "test"; // TODO: replace with secure config
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
            CompletableFuture.runAsync(() -> {
                log.info("Sending notifications to {} subscribers", subscribers.size());
                for (Subscriber sub : subscribers.values()) {
                    log.info("Sending daily NBA scores email to {}", sub.getEmail());
                    // TODO: send actual HTML formatted email
                }
            });
        } catch (Exception e) {
            log.error("Error parsing NBA API response: {}", e.getMessage(), e);
        }
    }

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

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ConfirmTokenRequest {
        @NotBlank
        private String token;
    }

    @Data
    public static class UnsubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    public static class SchedulerConfigRequest {
        @NotBlank
        private String cronExpression;
        private boolean enabled;
    }

    @Data
    @AllArgsConstructor
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
    @AllArgsConstructor
    public static class SchedulerConfig {
        private String cronExpression;
        private boolean enabled;
    }
}