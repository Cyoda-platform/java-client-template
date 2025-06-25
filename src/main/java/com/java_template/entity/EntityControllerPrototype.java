package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesStore = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String NBA_API_KEY = "test"; // TODO: secure storage
    private static final String NBA_API_URL_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + NBA_API_KEY;

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
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchGamesRequest {
        @NotBlank
        @Pattern(regexp = "\d{4}-\d{2}-\d{2}", message = "Invalid date format, expected YYYY-MM-DD")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchGamesResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private Date subscribedAt;
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
        private String otherInfo;
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
    static class GamesResponse {
        private List<Game> games;
        private Integer page;
        private Integer size;
        private Integer total;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail();
        subscribers.putIfAbsent(email.toLowerCase(Locale.ROOT), new Subscriber(email, new Date()));
        logger.info("New subscription added: {}", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        List<String> allEmails = new ArrayList<>(subscribers.keySet());
        logger.info("Retrieved {} subscribers", allEmails.size());
        return ResponseEntity.ok(new SubscribersResponse(allEmails));
    }

    @GetMapping("/games/all")
    public ResponseEntity<GamesResponse> getAllGames(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer size) {
        List<Game> allGames = new ArrayList<>();
        gamesStore.values().forEach(allGames::addAll);
        int total = allGames.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Game> pageGames = allGames.subList(fromIndex, toIndex);
        logger.info("Retrieved all games page {} size {} total {}", page, size, total);
        return ResponseEntity.ok(new GamesResponse(pageGames, page, size, total));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<GamesResponse> getGamesByDate(
            @PathVariable @Pattern(regexp = "\d{4}-\d{2}-\d{2}", message = "Invalid date format, expected YYYY-MM-DD") String date) {
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format requested: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        List<Game> games = gamesStore.getOrDefault(date, Collections.emptyList());
        logger.info("Retrieved {} games for date {}", games.size(), date);
        return ResponseEntity.ok(new GamesResponse(games, null, null, games.size()));
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchGamesResponse> fetchAndStoreScores(@RequestBody @Valid FetchGamesRequest request) {
        String date = request.getDate();
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format for fetch: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        logger.info("Received request to fetch NBA scores for date {}", date);
        CompletableFuture.runAsync(() -> fetchStoreAndNotify(date)); // async processing
        return ResponseEntity.ok(new FetchGamesResponse(
                "Scores fetched, saved, and notifications sent (async)", date, -1));
    }

    @Async
    void fetchStoreAndNotify(String date) {
        try {
            String url = String.format(NBA_API_URL_TEMPLATE, date);
            String rawJson = restTemplate.getForObject(url, String.class);
            if (rawJson == null || rawJson.isBlank()) {
                logger.warn("Empty response from NBA API for date {}", date);
                return;
            }
            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                logger.error("Unexpected NBA API response format for date {}: expected JSON array", date);
                return;
            }
            List<Game> fetchedGames = new ArrayList<>();
            for (JsonNode node : rootNode) {
                fetchedGames.add(parseGameFromJsonNode(node, date));
            }
            gamesStore.put(date, fetchedGames);
            logger.info("Stored {} games for date {}", fetchedGames.size(), date);
            notifySubscribers(date, fetchedGames);
        } catch (Exception e) {
            logger.error("Error during fetchStoreAndNotify for date " + date, e);
        }
    }

    private Game parseGameFromJsonNode(JsonNode node, String date) {
        String homeTeam = node.path("HomeTeam").asText(null);
        String awayTeam = node.path("AwayTeam").asText(null);
        Integer homeScore = node.path("HomeTeamScore").isInt() ? node.path("HomeTeamScore").asInt() : null;
        Integer awayScore = node.path("AwayTeamScore").isInt() ? node.path("AwayTeamScore").asInt() : null;
        String otherInfo = node.toString();
        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, otherInfo);
    }

    private void notifySubscribers(String date, List<Game> games) {
        List<String> emails = new ArrayList<>(subscribers.keySet());
        if (emails.isEmpty()) {
            logger.info("No subscribers to notify for date {}", date);
            return;
        }
        StringBuilder summary = new StringBuilder();
        summary.append("NBA Scores for ").append(date).append(":
");
        for (Game g : games) {
            summary.append(String.format("%s vs %s: %d - %d
",
                    g.getHomeTeam(), g.getAwayTeam(),
                    Optional.ofNullable(g.getHomeScore()).orElse(-1),
                    Optional.ofNullable(g.getAwayScore()).orElse(-1)));
        }
        for (String email : emails) {
            logger.info("Sending email to {}: 
{}", email, summary);
            // TODO: integrate real email sending
        }
        logger.info("Email notifications sent to {} subscribers for date {}", emails.size(), date);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", ex.getStatusCode().value());
        // Convert HttpStatusCode to HttpStatus to get reason phrase safely
        HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode().value());
        err.put("error", httpStatus != null ? httpStatus.getReasonPhrase() : "Error");
        err.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        err.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}
