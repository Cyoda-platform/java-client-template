package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final String NBA_API_KEY = "test"; // TODO: secure config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    private final Map<String, Subscriber> subscribers = new HashMap<>(); // local cache for subscribers only, as no replacement provided
    private final Map<String, List<Game>> gamesByDate = new HashMap<>();  // local cache for games only, no replacement possible

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private OffsetDateTime subscribedAt;
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
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    @AllArgsConstructor
    static class FetchScoresResponse {
        private String message;
    }

    @PostConstruct
    void initDemo() {
        subscribers.put("demo@example.com", new Subscriber("demo@example.com", OffsetDateTime.now()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase();
        log.info("Subscription request for {}", email);
        if (subscribers.containsKey(email)) {
            return ResponseEntity.ok(new SubscribeResponse("Email already subscribed", email));
        }
        subscribers.put(email, new Subscriber(email, OffsetDateTime.now()));
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    @PostMapping("/scores/fetch")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        String date = request.getDate();
        try {
            LocalDate.parse(date);
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid date format, expected YYYY-MM-DD");
        }
        CompletableFuture.runAsync(() -> fetchStoreAndNotify(date));
        return ResponseEntity.ok(new FetchScoresResponse("Scores fetching started for date " + date));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<String>> getAllSubscribers() {
        return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
    }

    @GetMapping("/games/all")
    public ResponseEntity<Map<String, Object>> getAllGames(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        List<Game> all = new ArrayList<>();
        gamesByDate.values().forEach(all::addAll);
        int total = all.size();
        int pages = (int) Math.ceil((double) total / size);
        if (page > pages) page = pages;
        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        List<Game> content = from < to ? all.subList(from, to) : Collections.emptyList();
        Map<String, Object> resp = new HashMap<>();
        resp.put("page", page);
        resp.put("size", size);
        resp.put("totalPages", pages);
        resp.put("totalGames", total);
        resp.put("games", content);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<Map<String, Object>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        List<Game> list = gamesByDate.getOrDefault(date, Collections.emptyList());
        Map<String, Object> resp = new HashMap<>();
        resp.put("date", date);
        resp.put("games", list);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/unsubscribe")
    public ResponseEntity<Map<String, String>> deleteSubscription(@RequestParam @NotBlank @Email String email) {
        String normalizedEmail = email.toLowerCase();
        log.info("Unsubscribe request for email: {}", normalizedEmail);
        Map<String, String> response = new HashMap<>();
        if (subscribers.remove(normalizedEmail) != null) {
            response.put("message", "Unsubscribed successfully");
            log.info("Email {} unsubscribed", normalizedEmail);
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "Email not found in subscribers");
            log.info("Email {} not found for unsubscribe", normalizedEmail);
            return ResponseEntity.status(404).body(response);
        }
    }

    @Async
    void fetchStoreAndNotify(String date) {
        try {
            String url = String.format(NBA_API_URL_TEMPLATE, date, NBA_API_KEY);
            String raw = restTemplate.getForObject(new URI(url), String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (root.isArray()) {
                List<Game> fetched = new ArrayList<>();
                root.forEach(node -> {
                    Game g = new Game();
                    g.setDate(date);
                    g.setHomeTeam(node.path("HomeTeam").asText(null));
                    g.setAwayTeam(node.path("AwayTeam").asText(null));
                    g.setHomeScore(node.path("HomeTeamScore").asInt(0));
                    g.setAwayScore(node.path("AwayTeamScore").asInt(0));
                    g.setStatus(node.path("Status").asText(null));
                    fetched.add(g);
                });
                gamesByDate.put(date, fetched);
                CompletableFuture.runAsync(() -> sendEmailNotifications(date, fetched)); // fire-and-forget
            }
        } catch (Exception e) {
            log.error("Error fetching scores: {}", e.toString());
        }
    }

    private void sendEmailNotifications(String date, List<Game> games) {
        StringBuilder sb = new StringBuilder("NBA Scores for ").append(date).append(":\n");
        games.forEach(g -> sb.append(String.format("%s vs %s: %d-%d [%s]\n",
                g.getHomeTeam(), g.getAwayTeam(),
                g.getHomeScore(), g.getAwayScore(),
                g.getStatus())));
        subscribers.keySet().forEach(email -> log.info("Email to {}: {}", email, sb));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        log.error("Status exception: {}", ex.toString());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        log.error("Unhandled exception: ", ex);
        return new ResponseEntity<>(err, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}