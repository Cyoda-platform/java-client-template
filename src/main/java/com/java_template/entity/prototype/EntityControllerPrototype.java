package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + EXTERNAL_API_KEY;

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<LocalDate, List<Game>> gamesByDate = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // DTOs

    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
    }

    public static class Subscriber {
        private String email;
        private LocalDate subscribedAt;
        public Subscriber(String email, LocalDate subscribedAt) {
            this.email = email;
            this.subscribedAt = subscribedAt;
        }
        public String getEmail() { return email; }
        public LocalDate getSubscribedAt() { return subscribedAt; }
    }

    public static class Game {
        private LocalDate date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        public Game(LocalDate date, String homeTeam, String awayTeam, Integer homeScore, Integer awayScore) {
            this.date = date;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
        }
        public LocalDate getDate() { return date; }
        public String getHomeTeam() { return homeTeam; }
        public String getAwayTeam() { return awayTeam; }
        public Integer getHomeScore() { return homeScore; }
        public Integer getAwayScore() { return awayScore; }
    }

    public static class FetchScoresResponse {
        private String date;
        private int gamesFetched;
        private int subscribersNotified;
        public FetchScoresResponse(String date, int gamesFetched, int subscribersNotified) {
            this.date = date;
            this.gamesFetched = gamesFetched;
            this.subscribersNotified = subscribersNotified;
        }
        public String getDate() { return date; }
        public int getGamesFetched() { return gamesFetched; }
        public int getSubscribersNotified() { return subscribersNotified; }
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Subscribe request received for email: {}", request.getEmail());
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        if (subscribers.containsKey(email)) {
            logger.error("Email {} is already subscribed", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already subscribed");
        }
        subscribers.put(email, new Subscriber(email, LocalDate.now()));
        logger.info("Subscribed new email: {}", email);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/subscribers")
    public List<String> getSubscribers() {
        logger.info("Fetching all subscriber emails");
        return new ArrayList<>(subscribers.keySet());
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetch scores request for date: {}", request.getDate());
        LocalDate requestedDate;
        try {
            requestedDate = LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: {}", request.getDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        CompletableFuture.runAsync(() -> fetchAndNotify(requestedDate)); // async fire-and-forget
        return ResponseEntity.ok(new FetchScoresResponse(request.getDate(), -1, subscribers.size()));
    }

    @GetMapping("/games/all")
    public List<Game> getAllGames() {
        logger.info("Fetching all games stored");
        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        return allGames;
    }

    @GetMapping("/games/{date}")
    public List<Game> getGamesByDate(@PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Fetching games for date: {}", date);
        LocalDate queryDate;
        try {
            queryDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format in path: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        return gamesByDate.getOrDefault(queryDate, Collections.emptyList());
    }

    // Internal methods

    private void fetchAndNotify(LocalDate date) {
        logger.info("Starting fetch and notify for date: {}", date);
        try {
            String url = String.format(EXTERNAL_API_URL_TEMPLATE, date);
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                logger.error("Empty response from external API for date: {}", date);
                return;
            }
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (!root.isArray()) {
                logger.error("Unexpected JSON structure from external API for date: {}", date);
                return;
            }
            List<Game> fetchedGames = new ArrayList<>();
            for (JsonNode gameNode : root) {
                String homeTeam = safeGetText(gameNode, "HomeTeam");
                String awayTeam = safeGetText(gameNode, "AwayTeam");
                Integer homeScore = safeGetInt(gameNode, "HomeTeamScore");
                Integer awayScore = safeGetInt(gameNode, "AwayTeamScore");
                if (homeTeam == null || awayTeam == null) {
                    logger.warn("Skipping game with incomplete team info: {}", gameNode.toString());
                    continue;
                }
                fetchedGames.add(new Game(date, homeTeam, awayTeam, homeScore, awayScore));
            }
            gamesByDate.put(date, fetchedGames);
            logger.info("Stored {} games for date {}", fetchedGames.size(), date);
            sendEmailNotifications(date, fetchedGames);
        } catch (Exception ex) {
            logger.error("Error during fetch and notify process for date {}: {}", date, ex.getMessage(), ex);
        }
    }

    private String safeGetText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Integer safeGetInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull() && f.isInt()) ? f.asInt() : null;
    }

    private void sendEmailNotifications(LocalDate date, List<Game> games) {
        logger.info("Preparing to send email notifications to {} subscribers for date {}", subscribers.size(), date);
        subscribers.keySet().forEach(email -> {
            CompletableFuture.runAsync(() -> {
                logger.info("Sending email to {} with {} games summary for {}", email, games.size(), date);
                // TODO: Implement real email sending here
            });
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}