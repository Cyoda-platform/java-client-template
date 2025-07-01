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
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private static final String API_KEY = "test"; // TODO: secure key
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        @Email
        @NotBlank
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
    public static class FetchGamesRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchGamesResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String email;
        private OffsetDateTime subscribedAt;
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
        private String otherDetails;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        log.info("Subscription request for: {}", email);
        if (subscribers.containsKey(email)) {
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", email));
        }
        subscribers.put(email, new Subscriber(email, OffsetDateTime.now()));
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchGamesResponse> fetchAndStoreGames(@RequestBody(required = false) @Valid FetchGamesRequest request) {
        String dateStr = Optional.ofNullable(request).map(FetchGamesRequest::getDate).filter(s -> !s.isBlank())
            .orElse(LocalDate.now().toString());
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        CompletableFuture.runAsync(() -> fetchStoreNotify(date));
        int currentCount = gamesByDate.getOrDefault(dateStr, Collections.emptyList()).size();
        return ResponseEntity.ok(new FetchGamesResponse("Async fetch/store/notify started", dateStr, currentCount));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<String>> getSubscribers() {
        return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
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
        @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD") String date) {
        return ResponseEntity.ok(gamesByDate.getOrDefault(date, Collections.emptyList()));
    }

    private void fetchStoreNotify(LocalDate date) {
        String dateStr = date.toString();
        String url = String.format(NBA_API_URL_TEMPLATE, dateStr, API_KEY);
        try {
            URI uri = new URI(url);
            String resp = restTemplate.getForObject(uri, String.class);
            if (resp == null) return;
            JsonNode root = objectMapper.readTree(resp);
            List<Game> list = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(node -> list.add(parseGame(node, dateStr)));
            } else {
                list.add(parseGame(root, dateStr));
            }
            gamesByDate.put(dateStr, list);
            sendNotifications(dateStr, list);
        } catch (URISyntaxException e) {
            log.error("URI error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Fetch/store error", e);
        }
    }

    private Game parseGame(JsonNode node, String dateStr) {
        String home = node.path("HomeTeam").asText(null);
        String away = node.path("AwayTeam").asText(null);
        Integer hs = node.path("HomeTeamScore").isNull() ? null : node.get("HomeTeamScore").asInt();
        Integer as = node.path("AwayTeamScore").isNull() ? null : node.get("AwayTeamScore").asInt();
        Map<String, Object> other = new HashMap<>();
        node.fieldNames().forEachRemaining(f -> {
            if (!List.of("HomeTeam","AwayTeam","HomeTeamScore","AwayTeamScore").contains(f)) {
                other.put(f, node.get(f));
            }
        });
        String details;
        try { details = objectMapper.writeValueAsString(other); }
        catch (Exception e) { details = "{}"; }
        return new Game(dateStr, home, away, hs, as, details);
    }

    @Async
    public void sendNotifications(String dateStr, List<Game> games) {
        if (subscribers.isEmpty() || games.isEmpty()) return;
        StringBuilder sb = new StringBuilder("NBA Scores for ").append(dateStr).append(":\n");
        games.forEach(g -> sb.append(String.format("%s vs %s: %s-%s\n",
            g.getHomeTeam(), g.getAwayTeam(),
            g.getHomeScore()==null?"N/A":g.getHomeScore(),
            g.getAwayScore()==null?"N/A":g.getAwayScore())));
        subscribers.keySet().forEach(email -> log.info("Email to {}:\n{}", email, sb));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handle(ResponseStatusException ex) {
        Map<String,String> err = Map.of(
            "error", ex.getStatusCode().toString(),
            "message", ex.getReason()
        );
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleAll(Exception ex) {
        Map<String,String> err = Map.of(
            "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
            "message", "Internal server error"
        );
        return new ResponseEntity<>(err, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}