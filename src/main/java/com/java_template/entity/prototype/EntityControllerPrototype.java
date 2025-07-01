package com.java_template.entity.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Async;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping("/prototype")
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        @NotBlank
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be in YYYY-MM-DD format")
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
    public static class Subscriber {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Game {
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
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
    public static class GamesResponse {
        private List<Game> games;
        private Integer page;
        private Integer size;
        private Integer total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesByDateResponse {
        private String date;
        private List<Game> games;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());
        String email = request.getEmail().toLowerCase();
        if (subscribers.containsKey(email)) {
            logger.info("Email already subscribed: {}", email);
            return ResponseEntity.ok(new SubscribeResponse("Email already subscribed"));
        }
        subscribers.put(email, new Subscriber(email));
        logger.info("Email subscribed successfully: {}", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful"));
    }

    @GetMapping("/subscribers")
    public SubscribersResponse getSubscribers() {
        logger.info("Fetching all subscribers, count={}", subscribers.size());
        return new SubscribersResponse(new ArrayList<>(subscribers.keySet()));
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetch scores requested for date: {}", request.getDate());
        CompletableFuture.runAsync(() -> fetchStoreNotify(request.getDate()));
        return ResponseEntity.ok(new FetchScoresResponse("Scores fetching started", request.getDate(), 0));
    }

    @GetMapping("/games/all")
    public GamesResponse getAllGames(
        @RequestParam(required = false, defaultValue = "1") @Min(1) Integer page,
        @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        logger.info("Fetching all games - page: {}, size: {}", page, size);
        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        int total = allGames.size();
        int from = (page - 1) * size;
        if (from >= total) {
            return new GamesResponse(Collections.emptyList(), page, size, total);
        }
        int to = Math.min(from + size, total);
        return new GamesResponse(allGames.subList(from, to), page, size, total);
    }

    @GetMapping("/games/{date}")
    public GamesByDateResponse getGamesByDate(
        @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be in YYYY-MM-DD format") String date
    ) {
        logger.info("Fetching games for date: {}", date);
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return new GamesByDateResponse(date, games);
    }

    @Async
    void fetchStoreNotify(String dateStr) {
        logger.info("Starting async fetch-store-notify for date: {}", dateStr);
        try {
            String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test", dateStr);
            String jsonResponse = restTemplate.getForObject(new URI(url), String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (!root.isArray()) {
                logger.error("Unexpected response format");
                return;
            }
            List<Game> fetched = new ArrayList<>();
            for (JsonNode node : root) {
                String id = node.path("GameID").asText();
                String home = node.path("HomeTeam").asText();
                String away = node.path("AwayTeam").asText();
                Integer hScore = node.path("HomeTeamScore").isInt() ? node.path("HomeTeamScore").asInt() : null;
                Integer aScore = node.path("AwayTeamScore").isInt() ? node.path("AwayTeamScore").asInt() : null;
                fetched.add(new Game(id, dateStr, home, away, hScore, aScore));
            }
            gamesByDate.put(dateStr, fetched);
            notifySubscribers(dateStr, fetched);
            logger.info("Completed async process for date: {}", dateStr);
        } catch (Exception e) {
            logger.error("Error in async process", e);
        }
    }

    void notifySubscribers(String date, List<Game> games) {
        if (subscribers.isEmpty()) return;
        StringBuilder content = new StringBuilder("Daily NBA Scores for ").append(date).append(":\n\n");
        for (Game g : games) {
            content.append(g.getAwayTeam()).append(" @ ").append(g.getHomeTeam())
                .append(": ").append(Objects.toString(g.getAwayScore(), "N/A"))
                .append(" - ").append(Objects.toString(g.getHomeScore(), "N/A")).append("\n");
        }
        subscribers.values().forEach(sub ->
            logger.info("Sending email to {}:\n{}", sub.getEmail(), content));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String,String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleGenericException(Exception ex) {
        Map<String,String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}