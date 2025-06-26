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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private static final String NBA_API_KEY = "test"; // TODO: Replace with config/env variable
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, LocalDate> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Invalid date format. Use YYYY-MM-DD.")
        private String date;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        log.info("Received subscription for email: {}", request.getEmail());
        subscribers.putIfAbsent(request.getEmail().toLowerCase(Locale.ROOT), LocalDate.now());
        log.info("Subscription successful for email: {}", request.getEmail());
        return ResponseEntity.ok(new MessageResponse("Subscription successful"));
    }

    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getSubscribers() {
        log.info("Retrieving subscribers");
        return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
    }

    @GetMapping(path = "/games/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(name = "page", required = false, defaultValue = "0") @Min(value = 0, message = "Page index must be >= 0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "100") @Min(value = 1, message = "Size must be >= 1") int size) {
        log.info("Fetching all games page={} size={}", page, size);
        List<Game> all = new ArrayList<>();
        gamesByDate.values().forEach(all::addAll);
        int from = page * size;
        if (from >= all.size()) return ResponseEntity.ok(Collections.emptyList());
        int to = Math.min(from + size, all.size());
        return ResponseEntity.ok(all.subList(from, to));
    }

    @GetMapping(path = "/games/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getGamesByDate(
            @PathVariable("date") @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Invalid date format. Use YYYY-MM-DD.") String dateStr) {
        log.info("Fetching games for date: {}", dateStr);
        try {
            LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM-DD.");
        }
        return ResponseEntity.ok(gamesByDate.getOrDefault(dateStr, Collections.emptyList()));
    }

    @PostMapping(path = "/scores/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        log.info("Starting fetch for date: {}", request.getDate());
        LocalDate date = LocalDate.parse(request.getDate());
        CompletableFuture.runAsync(() -> {
            try { fetchStoreAndNotify(date); }
            catch (Exception e) { log.error("Async error: {}", e.getMessage(), e); }
        });
        return ResponseEntity.ok(new MessageResponse("Scores fetching started"));
    }

    private void fetchStoreAndNotify(LocalDate date) throws URISyntaxException {
        String dateStr = date.toString();
        String url = String.format(NBA_API_URL_TEMPLATE, dateStr, NBA_API_KEY);
        log.info("Calling external API: {}", url);
        URI uri = new URI(url);
        String raw;
        try { raw = restTemplate.getForObject(uri, String.class); }
        catch (Exception e) { log.error("Fetch failed: {}", e.getMessage()); return; }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) { log.warn("Expected array, got {}", root.getNodeType()); return; }
            List<Game> list = new ArrayList<>();
            for (JsonNode node : root) {
                Game g = parseGame(node);
                if (g != null) list.add(g);
            }
            gamesByDate.put(dateStr, list);
            log.info("Stored {} games for {}", list.size(), dateStr);
            notifySubscribers(dateStr, list);
        } catch (Exception ex) { log.error("Parse error: {}", ex.getMessage()); }
    }

    private Game parseGame(JsonNode n) {
        try {
            String date = Optional.ofNullable(n.path("Day").asText(null))
                    .or(() -> Optional.ofNullable(n.path("DateTime").asText(null)).filter(s -> s.length() >= 10).map(s -> s.substring(0,10)))
                    .orElse(null);
            String home = n.path("HomeTeam").asText(null);
            String away = n.path("AwayTeam").asText(null);
            Integer hs = n.has("HomeTeamScore") ? n.get("HomeTeamScore").asInt() : null;
            Integer as = n.has("AwayTeamScore") ? n.get("AwayTeamScore").asInt() : null;
            String status = n.path("Status").asText("Unknown");
            if (date==null||home==null||away==null) { log.warn("Incomplete game: {}", n); return null; }
            return new Game(date, home, away, hs, as, status);
        } catch (Exception e) {
            log.warn("Parse exception: {}", e.getMessage());
            return null;
        }
    }

    private void notifySubscribers(String dateStr, List<Game> games) {
        if (subscribers.isEmpty()) { log.info("No subscribers for {}", dateStr); return; }
        StringBuilder sb = new StringBuilder("NBA Scores for ").append(dateStr).append(":\n\n");
        games.forEach(g -> sb.append(String.format("%s vs %s: %d - %d (%s)\n",
                g.getAwayTeam(), g.getHomeTeam(),
                Optional.ofNullable(g.getAwayScore()).orElse(0),
                Optional.ofNullable(g.getHomeScore()).orElse(0),
                g.getStatus())));
        subscribers.keySet().forEach(email -> log.info("Sending to {}:\n{}", email, sb.toString())); // TODO: integrate real email service
        log.info("Notifications sent to {} subscribers", subscribers.size());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        log.error("Error {}: {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }
}
