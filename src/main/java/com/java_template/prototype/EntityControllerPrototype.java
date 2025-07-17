package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String API_KEY = "test"; // TODO: secure config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + API_KEY;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<GameScore>> gamesByDate = new ConcurrentHashMap<>();

    @Data
    public static class SubscribeRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    public static class FetchScoresResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    public static class Subscriber {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class GameScore {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Subscription request for email: {}", request.getEmail());
        subscribers.putIfAbsent(request.getEmail().toLowerCase(Locale.ROOT), new Subscriber() {{
            setEmail(request.getEmail().toLowerCase(Locale.ROOT));
        }});
        SubscribeResponse resp = new SubscribeResponse();
        resp.setMessage("Subscription successful");
        resp.setEmail(request.getEmail());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<Map<String, Collection<String>>> getSubscribers() {
        logger.info("Retrieving subscribers");
        return ResponseEntity.ok(Map.of("subscribers", subscribers.keySet()));
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetch scores request for date: {}", request.getDate());
        LocalDate fetchDate;
        try {
            fetchDate = LocalDate.parse(request.getDate());
        } catch (Exception e) {
            logger.error("Invalid date: {}", request.getDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format");
        }
        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(fetchDate);
            } catch (Exception ex) {
                logger.error("Error in async fetchStoreAndNotify: {}", ex.getMessage(), ex);
            }
        });
        FetchScoresResponse resp = new FetchScoresResponse();
        resp.setMessage("Scores fetching started asynchronously");
        resp.setDate(request.getDate());
        resp.setGamesCount(0);
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/games/all")
    public ResponseEntity<Map<String, Object>> getAllGames(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        logger.info("Retrieving all games with limit {} and offset {}", limit, offset);
        List<GameScore> all = new ArrayList<>();
        gamesByDate.values().forEach(all::addAll);
        all.sort(Comparator.comparing(GameScore::getDate).reversed());
        int total = all.size();
        int from = Math.min(offset, total);
        int to = Math.min(offset + limit, total);
        List<GameScore> page = all.subList(from, to);
        return ResponseEntity.ok(Map.of("games", page, "limit", limit, "offset", offset, "total", total));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<List<GameScore>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be YYYY-MM-DD") String date) {
        logger.info("Retrieving games for date: {}", date);
        return ResponseEntity.ok(gamesByDate.getOrDefault(date, Collections.emptyList()));
    }

    private void fetchStoreAndNotify(LocalDate date) throws Exception {
        String dateStr = date.toString();
        logger.info("Starting fetchStoreAndNotify for {}", dateStr);
        String url = String.format(NBA_API_URL_TEMPLATE, dateStr);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            logger.error("External API status: {}", res.statusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch NBA scores");
        }
        JsonNode root = objectMapper.readTree(res.body());
        List<GameScore> parsed = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                GameScore g = new GameScore();
                g.setDate(dateStr);
                g.setHomeTeam(getText(node, "HomeTeam"));
                g.setAwayTeam(getText(node, "AwayTeam"));
                g.setHomeScore(getInt(node, "HomeTeamScore"));
                g.setAwayScore(getInt(node, "AwayTeamScore"));
                parsed.add(g);
            }
        }
        gamesByDate.put(dateStr, parsed);
        sendEmailNotifications(dateStr, parsed);
    }

    private String getText(JsonNode n, String f) { JsonNode v = n.get(f); return v != null && !v.isNull()? v.asText(): null; }
    private Integer getInt(JsonNode n, String f) { JsonNode v = n.get(f); return v != null && v.isInt()? v.asInt(): null; }

    private void sendEmailNotifications(String date, List<GameScore> games) {
        logger.info("Sending emails to {} subscribers", subscribers.size());
        StringBuilder sum = new StringBuilder("NBA Scores for ").append(date).append(":\n");
        for (GameScore g: games) {
            sum.append(String.format("%s vs %s: %d - %d\n",
                g.getHomeTeam(), g.getAwayTeam(),
                Optional.ofNullable(g.getHomeScore()).orElse(0),
                Optional.ofNullable(g.getAwayScore()).orElse(0)));
        }
        for (Subscriber s: subscribers.values()) {
            // async email send simulation
            CompletableFuture.runAsync(() -> {
                logger.info("Email to {}:\n{}", s.getEmail(), sum);
                // TODO: integrate real email service
            });
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> onStatusEx(ResponseStatusException ex) {
        logger.error("Handled error: {}", ex.getMessage());
        return new ResponseEntity<>(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()), ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> onEx(Exception ex) {
        logger.error("Unhandled exception", ex);
        return new ResponseEntity<>(Map.of("error", HttpStatus.INTERNAL_SERVER_ERROR.toString(), "message","Internal server error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}