package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, Instant> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<JsonNode>> gamesByDate = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_KEY = "test"; // TODO: replace with secure key storage
    private static final String NBA_API_ENDPOINT_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    @Data
    public static class SubscriptionRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscriptionRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT).trim();
        if (subscribers.containsKey(email)) {
            logger.info("Subscription attempt for existing email: {}", email);
            return ResponseEntity.ok(Map.of("message", "Already subscribed", "email", email));
        }
        subscribers.put(email, Instant.now());
        logger.info("New subscriber added: {}", email);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Subscription successful", "email", email));
    }

    @PostMapping(path = "/fetch-scores", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        String dateStr = request.getDate();
        logger.info("Received fetch request for date: {}", dateStr);
        CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(String.format(NBA_API_ENDPOINT_TEMPLATE, dateStr, API_KEY));
                JsonNode responseJson = restTemplate.getForObject(uri, JsonNode.class);
                if (responseJson == null || !responseJson.isArray()) {
                    logger.error("Invalid response from external API for date {}: {}", dateStr, responseJson);
                    return;
                }
                List<JsonNode> games = new ArrayList<>();
                responseJson.forEach(games::add);
                gamesByDate.put(dateStr, games);
                logger.info("Stored {} games for date {}", games.size(), dateStr);
                sendEmailNotifications(dateStr, games);
            } catch (Exception ex) {
                logger.error("Error during fetchScores async for date " + dateStr, ex);
            }
        });
        return ResponseEntity.accepted().body(Map.of("message", "Fetch triggered", "date", dateStr));
    }

    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<String>>> getSubscribers() {
        List<String> emails = new ArrayList<>(subscribers.keySet());
        emails.sort(String::compareToIgnoreCase);
        logger.info("Retrieving {} subscribers", emails.size());
        return ResponseEntity.ok(Map.of("subscribers", emails));
    }

    @GetMapping(path = "/games/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<JsonNode>>> getAllGames() {
        logger.info("Retrieving all games across {} dates", gamesByDate.size());
        return ResponseEntity.ok(gamesByDate);
    }

    @GetMapping(path = "/games/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD") String date) {
        logger.info("Retrieving games for date: {}", date);
        List<JsonNode> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(Map.of("date", date, "games", games));
    }

    private void sendEmailNotifications(String date, List<JsonNode> games) {
        logger.info("Preparing notifications for {} subscribers on {}", subscribers.size(), date);
        if (subscribers.isEmpty()) {
            logger.info("No subscribers to notify");
            return;
        }
        StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
        games.forEach(game -> {
            String home = game.path("HomeTeam").asText("N/A");
            String away = game.path("AwayTeam").asText("N/A");
            String homeScore = game.path("HomeScore").asText("?");
            String awayScore = game.path("AwayScore").asText("?");
            summary.append(String.format("%s vs %s: %s - %s%n", away, home, awayScore, homeScore));
        });
        CompletableFuture.runAsync(() -> {
            subscribers.keySet().forEach(email -> {
                // TODO: implement real email send
                logger.info("Mock sending to {}: \n{}", email, summary);
            });
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, reason={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }
}