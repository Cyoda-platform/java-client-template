package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
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
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        @NotBlank
        @Email
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
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
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
    }

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

    @GetMapping("/subscribers")
    public SubscribersResponse getSubscribers() {
        log.info("Retrieving all subscribers, count={}", subscribers.size());
        return new SubscribersResponse(new ArrayList<>(subscribers.keySet()));
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchScoresResponse> fetchAndStoreScores(@Valid @RequestBody FetchScoresRequest request) {
        String dateStr = (request.getDate() != null) ? request.getDate() : java.time.LocalDate.now().toString();
        log.info("Fetching NBA scores for date: {}", dateStr);

        List<Game> fetchedGames;
        try {
            fetchedGames = fetchScoresFromExternalApi(dateStr);
        } catch (Exception ex) {
            log.error("Failed to fetch NBA scores from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch from external API");
        }

        gamesByDate.put(dateStr, fetchedGames);
        log.info("Stored {} games for date {}", fetchedGames.size(), dateStr);

        CompletableFuture.runAsync(() -> sendNotifications(dateStr, fetchedGames)); // TODO: use proper async handling
        return ResponseEntity.ok(new FetchScoresResponse("Scores fetched, stored and notifications sent", dateStr, fetchedGames.size()));
    }

    @GetMapping("/games/all")
    public GamesResponse getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        log.info("Retrieving all games - page: {}, size: {}", page, size);
        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allGames.size());
        List<Game> pageGames = fromIndex >= allGames.size() ? Collections.emptyList() : allGames.subList(fromIndex, toIndex);
        return new GamesResponse(page, size, allGames.size(), pageGames);
    }

    @GetMapping("/games/{date}")
    public GamesByDateResponse getGamesByDate(
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
            @PathVariable String date) {
        log.info("Retrieving games for date {}", date);
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return new GamesByDateResponse(date, games);
    }

    private List<Game> fetchScoresFromExternalApi(String date) throws Exception {
        String url = String.format(EXTERNAL_API_URL_TEMPLATE, date, API_KEY);
        log.info("Calling external API: {}", url);
        String rawJson = restTemplate.getForObject(new URI(url), String.class);
        if (rawJson == null) throw new IllegalStateException("Empty response");
        JsonNode rootNode = objectMapper.readTree(rawJson);
        List<Game> gameList = new ArrayList<>();
        if (rootNode.isArray()) {
            for (JsonNode gameNode : rootNode) {
                gameList.add(parseGame(gameNode, date));
            }
        } else if (rootNode.isObject()) {
            gameList.add(parseGame(rootNode, date));
        }
        return gameList;
    }

    private Game parseGame(JsonNode node, String date) {
        return new Game(
                date,
                safeText(node, "HomeTeam"),
                safeText(node, "AwayTeam"),
                safeInt(node, "HomeTeamScore"),
                safeInt(node, "AwayTeamScore"),
                safeText(node, "Status")
        );
    }

    private String safeText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private Integer safeInt(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && child.isInt()) return child.asInt();
        if (child != null && child.isTextual()) {
            try { return Integer.parseInt(child.asText()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @Async
    public void sendNotifications(String date, List<Game> games) {
        log.info("Sending notifications for {} to {} subscribers", date, subscribers.size());
        StringBuilder content = new StringBuilder("NBA Scores for ").append(date).append(":\n");
        games.forEach(g -> content.append(String.format("%s vs %s: %s-%s (%s)\n",
                g.getHomeTeam(), g.getAwayTeam(), g.getHomeScore(), g.getAwayScore(), g.getStatus())));
        subscribers.values().forEach(sub -> log.info("Email to {}: \n{}", sub.getEmail(), content));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getStatusCode().toString());
        log.error("Error {}: {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "INTERNAL_SERVER_ERROR");
        error.put("message", ex.getMessage());
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}