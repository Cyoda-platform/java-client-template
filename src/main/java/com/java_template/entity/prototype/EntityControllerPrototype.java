package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<UUID, GameScore> storedGames = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscription {
        private String email;
        private OffsetDateTime subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameScore {
        private UUID id;
        private LocalDate date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
    }

    @Data
    static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    static class FetchScoresRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase();
        if (subscriptions.containsKey(email)) {
            logger.info("Duplicate subscription attempt for email: {}", email);
            return ResponseEntity.ok(Map.of("status","success","message","Subscription already exists."));
        }
        subscriptions.put(email, new Subscription(email, OffsetDateTime.now()));
        logger.info("New subscription added: {}", email);
        return ResponseEntity.ok(Map.of("status","success","message","Subscription added."));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<Map<String, List<String>>> getSubscribers() {
        List<String> emails = new ArrayList<>(subscriptions.keySet());
        logger.info("Returning {} subscribers", emails.size());
        return ResponseEntity.ok(Map.of("subscribers", emails));
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<Map<String, String>> fetchScores(@RequestBody(required = false) @Valid FetchScoresRequest request) {
        String dateStr = request != null ? request.getDate() : null;
        LocalDate date;
        try {
            date = (dateStr == null || dateStr.isBlank()) ? LocalDate.now() : LocalDate.parse(dateStr);
        } catch (Exception ex) {
            logger.error("Invalid date format: {}", dateStr);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }
        logger.info("Triggered fetchScores for date {}", date);
        CompletableFuture.runAsync(() -> fetchStoreAndNotify(date)); // TODO: adjust executor if necessary
        return ResponseEntity.ok(Map.of("status","success","message","Scores fetch and notification process started."));
    }

    @GetMapping("/games/all")
    public ResponseEntity<Map<String, Object>> getAllGames(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) int size,
        @RequestParam(required = false) @Size(min = 1) String team,
        @RequestParam(required = false) @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dateFrom,
        @RequestParam(required = false) @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dateTo
    ) {
        logger.info("Fetching games page={}, size={}, team={}, dateFrom={}, dateTo={}", page, size, team, dateFrom, dateTo);
        LocalDate from = null, to = null;
        try {
            if (dateFrom != null) from = LocalDate.parse(dateFrom);
            if (dateTo != null) to = LocalDate.parse(dateTo);
        } catch (Exception ex) {
            logger.error("Invalid dateFrom/dateTo format");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be YYYY-MM-DD");
        }
        List<GameScore> filtered = new ArrayList<>();
        for (GameScore g : storedGames.values()) {
            boolean ok = true;
            if (team != null && !g.getHomeTeam().toLowerCase().contains(team.toLowerCase())
                && !g.getAwayTeam().toLowerCase().contains(team.toLowerCase())) ok = false;
            if (from != null && g.getDate().isBefore(from)) ok = false;
            if (to != null && g.getDate().isAfter(to)) ok = false;
            if (ok) filtered.add(g);
        }
        int fromIdx = page * size;
        int toIdx = Math.min(fromIdx + size, filtered.size());
        if (fromIdx > filtered.size()) { fromIdx = filtered.size(); toIdx = filtered.size(); }
        List<GameScore> content = filtered.subList(fromIdx, toIdx);
        Map<String, Object> resp = new HashMap<>();
        resp.put("games", content);
        resp.put("pagination", Map.of("page", page, "size", size,
            "totalPages", (int)Math.ceil((double)filtered.size()/size),
            "totalElements", filtered.size()));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<Map<String, List<GameScore>>> getGamesByDate(
        @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date
    ) {
        // @Pattern ensures correct format for PathVariable
        LocalDate d;
        try { d = LocalDate.parse(date); }
        catch (Exception ex) {
            logger.error("Invalid date format: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }
        List<GameScore> result = new ArrayList<>();
        for (GameScore g : storedGames.values()) {
            if (g.getDate().equals(d)) result.add(g);
        }
        logger.info("Returning {} games for date {}", result.size(), date);
        return ResponseEntity.ok(Map.of("games", result));
    }

    @Async
    void fetchStoreAndNotify(LocalDate date) {
        logger.info("Started fetchStoreAndNotify for date {}", date);
        try {
            String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date + "?key=test";
            String raw = restTemplate.getForObject(new URI(url), String.class);
            JsonNode arr = objectMapper.readTree(raw);
            if (!arr.isArray()) {
                logger.warn("Expected array JSON but got {}", arr.getNodeType());
                return;
            }
            for (JsonNode n : arr) {
                String home = n.path("HomeTeam").asText(null);
                String away = n.path("AwayTeam").asText(null);
                Integer hScore = n.path("HomeTeamScore").isInt() ? n.path("HomeTeamScore").asInt() : null;
                Integer aScore = n.path("AwayTeamScore").isInt() ? n.path("AwayTeamScore").asInt() : null;
                String stat = n.path("Status").asText(null);
                if (home == null || away == null) continue;
                GameScore gs = new GameScore(UUID.randomUUID(), date, home, away, hScore, aScore, stat);
                storedGames.put(gs.getId(), gs);
            }
            notifySubscribers(date);
        } catch (Exception ex) {
            logger.error("Error in fetchStoreAndNotify", ex);
        }
    }

    private void notifySubscribers(LocalDate date) {
        StringBuilder sb = new StringBuilder("Daily NBA Scores for " + date + ":\n");
        List<GameScore> games = new ArrayList<>();
        for (GameScore g : storedGames.values()) if (g.getDate().equals(date)) games.add(g);
        games.forEach(g -> sb.append(String.format("%s vs %s: %d-%d\n",
            g.getAwayTeam(), g.getHomeTeam(),
            Optional.ofNullable(g.getAwayScore()).orElse(-1),
            Optional.ofNullable(g.getHomeScore()).orElse(-1))));
        String msg = sb.toString();
        subscriptions.keySet().forEach(email ->
            logger.info("Sending email to {}: \n{}", email, msg) // TODO replace with real email send
        );
        logger.info("Notifications sent to {} subscribers", subscriptions.size());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
            "error", ex.getStatusCode().toString(),
            "message", ex.getReason()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
            "message", "Internal server error"
        ));
    }
}