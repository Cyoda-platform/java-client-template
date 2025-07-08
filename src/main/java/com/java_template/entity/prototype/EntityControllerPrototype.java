package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    private static final String EXTERNAL_API_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        @Email
        @NotBlank
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Game {
        private String date;
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
        @NotBlank
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        @NotNull
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ApiResponse {
        private String status;
        private String message;
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<ApiResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetching scores for date: {}", request.getDate());
        String url = String.format(EXTERNAL_API_TEMPLATE, request.getDate());
        JsonNode apiResponse;
        try {
            apiResponse = objectMapper.readTree(restTemplate.getForObject(new URI(url), String.class));
        } catch (Exception e) {
            logger.error("Error fetching or parsing external data", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve external NBA data");
        }
        List<Game> games = new ArrayList<>();
        if (apiResponse.isArray()) {
            apiResponse.forEach(node -> {
                try {
                    Game g = new Game();
                    g.setDate(request.getDate());
                    g.setHomeTeam(node.path("HomeTeam").asText(null));
                    g.setAwayTeam(node.path("AwayTeam").asText(null));
                    g.setHomeScore(node.path("HomeTeamScore").isInt() ? node.path("HomeTeamScore").asInt() : null);
                    g.setAwayScore(node.path("AwayTeamScore").isInt() ? node.path("AwayTeamScore").asInt() : null);
                    Map<String, Object> additional = new HashMap<>();
                    node.fieldNames().forEachRemaining(field -> {
                        if (!List.of("HomeTeam","AwayTeam","HomeTeamScore","AwayTeamScore").contains(field)) {
                            additional.put(field, node.get(field));
                        }
                    });
                    g.setAdditionalInfo(additional);
                    games.add(g);
                } catch (Exception ex) {
                    logger.warn("Skipping malformed game entry", ex);
                }
            });
        } else {
            logger.warn("External response is not array for date {}", request.getDate());
        }
        gamesByDate.put(request.getDate(), games);
        logger.info("Stored {} games for {}", games.size(), request.getDate());
        sendNotificationsAsync(request.getDate(), games);
        return ResponseEntity.ok(new ApiResponse("success", "Scores fetched, stored, and notifications sent."));
    }

    @Async
    void sendNotificationsAsync(String date, List<Game> games) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Sending emails for date {} to {} subscribers", date, subscribers.size());
                StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
                games.forEach(g -> summary.append(String.format("%s vs %s: %s - %s\n",
                        g.getHomeTeam(), g.getAwayTeam(),
                        g.getHomeScore()!=null?g.getHomeScore():"?",
                        g.getAwayScore()!=null?g.getAwayScore():"?")));
                subscribers.values().forEach(sub -> {
                    // TODO replace with actual email logic
                    logger.info("Email to {}: \n{}", sub.getEmail(), summary);
                });
                logger.info("Emails sent");
            } catch (Exception e) {
                logger.error("Error sending notifications", e);
            }
        });
    }

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        subscribers.put(request.getEmail().toLowerCase(), new Subscriber(request.getEmail().toLowerCase(), Instant.now()));
        logger.info("Subscribed {}", request.getEmail());
        return ResponseEntity.ok(new ApiResponse("success", "Email subscribed successfully."));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<ApiResponse> deleteSubscription(@RequestParam @Email @NotBlank String email) {
        String key = email.toLowerCase();
        if (subscribers.remove(key) != null) {
            logger.info("Unsubscribed {}", email);
            return ResponseEntity.ok(new ApiResponse("success", "Email unsubscribed successfully."));
        } else {
            logger.info("Attempt to unsubscribe non-existing email {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("error", "Email not found in subscribers."));
        }
    }

    @GetMapping("/subscribers")
    public ResponseEntity<Collection<String>> getSubscribers() {
        return ResponseEntity.ok(subscribers.keySet());
    }

    @GetMapping("/games/all")
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        List<Game> all = new ArrayList<>();
        gamesByDate.values().forEach(all::addAll);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return ResponseEntity.ok(all.subList(from, to));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<List<Game>> getGamesByDate(
            @PathVariable
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be YYYY-MM-DD") String date) {
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(games);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleError(ResponseStatusException ex) {
        Map<String,String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        logger.error("Error {} {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }
}
