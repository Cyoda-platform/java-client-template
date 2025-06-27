package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        log.info("Received fetch-scores request for date={}", request.getDate());
        CompletableFuture.runAsync(() -> fetchStoreNotify(request.getDate()));
        return ResponseEntity.ok(new FetchScoresResponse("success", request.getDate(), -1));
    }

    @Async
    protected void fetchStoreNotify(String date) {
        log.info("Starting fetchStoreNotify for date={}", date);
        try {
            String url = String.format(EXTERNAL_API_TEMPLATE, date, API_KEY);
            URI uri = new URI(url);
            String rawJson = restTemplate.getForObject(uri, String.class);
            if (!StringUtils.hasText(rawJson)) {
                log.error("Empty response from external API for date {}", date);
                return;
            }
            JsonNode rootNode = objectMapper.readTree(rawJson);
            List<Game> games = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    games.add(parseGameFromJson(node));
                }
            } else {
                log.warn("Unexpected JSON structure from external API for date {}", date);
            }
            gamesByDate.put(date, games.isEmpty() ? Collections.emptyList() : games);
            log.info("Saved {} games for date {}", games.size(), date);
            sendNotifications(date, games);
        } catch (Exception e) {
            log.error("Error during fetchStoreNotify for date " + date, e);
        }
    }

    private Game parseGameFromJson(JsonNode node) {
        String homeTeam = node.path("HomeTeam").asText("");
        String awayTeam = node.path("AwayTeam").asText("");
        int homeScore = node.path("HomeTeamScore").asInt(-1);
        int awayScore = node.path("AwayTeamScore").asInt(-1);
        String dateStr = node.path("Day").asText("");
        return new Game(dateStr, homeTeam, awayTeam, homeScore, awayScore);
    }

    private void sendNotifications(String date, List<Game> games) {
        StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
        if (games.isEmpty()) {
            summary.append("No games played on this day.");
        } else {
            for (Game g : games) {
                summary.append(String.format("%s %d - %d %s\n", g.getHomeTeam(), g.getHomeScore(), g.getAwayScore(), g.getAwayTeam()));
            }
        }
        for (Subscriber sub : subscribers.values()) {
            log.info("Sending email to {} with summary:\n{}", sub.getEmail(), summary);
        }
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        log.info("Subscription request received for email={}", request.getEmail());
        subscribers.putIfAbsent(request.getEmail().toLowerCase(Locale.ROOT), new Subscriber(request.getEmail()));
        log.info("Email {} subscribed successfully", request.getEmail());
        return ResponseEntity.ok(new SubscribeResponse("subscribed", request.getEmail()));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        log.info("Retrieving all subscribers, count={}", subscribers.size());
        return ResponseEntity.ok(new SubscribersResponse(new ArrayList<>(subscribers.keySet())));
    }

    @GetMapping("/games/all")
    public ResponseEntity<GamesPageResponse> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        log.info("Retrieving all games with pagination page={}, size={}", page, size);
        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        int total = allGames.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<Game> content = allGames.subList(from, to);
        int totalPages = (total + size - 1) / size;
        return ResponseEntity.ok(new GamesPageResponse(page, size, totalPages, content));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<GamesByDateResponse> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        log.info("Retrieving games for date={}", date);
        return ResponseEntity.ok(new GamesByDateResponse(date, gamesByDate.getOrDefault(date, Collections.emptyList())));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", ex.getStatusCode().toString());
        log.error("Handled error: {}, status: {}", ex.getReason(), ex.getStatusCode());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        @NotNull
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresResponse {
        private String status;
        private String fetchedDate;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @NotBlank
        @Email
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