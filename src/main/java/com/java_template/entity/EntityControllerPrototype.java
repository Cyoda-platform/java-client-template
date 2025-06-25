package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final String API_KEY = "test"; // TODO: replace with real API key or config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();

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
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
    }

    @Data
    static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    static class FetchRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    static class NotificationRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
        private String email;
        private String date;
        private Integer gamesCount;
        private Integer emailsSent;
    }

    @PostMapping("/subscribe")
    public MessageResponse subscribe(@RequestBody @Valid SubscribeRequest request) {
        log.info("Subscribe request received for email={}", request.getEmail());
        subscribers.put(request.getEmail().toLowerCase(Locale.ROOT), new Subscriber(request.getEmail()));
        log.info("Email {} added to subscribers", request.getEmail());
        return new MessageResponse("Subscription successful", request.getEmail(), null, null, null);
    }

    @GetMapping("/subscribers")
    public Collection<String> getSubscribers() {
        log.info("Retrieving all subscribers");
        return subscribers.keySet();
    }

    @PostMapping("/games/fetch")
    public MessageResponse fetchAndStoreScores(@RequestBody @Valid FetchRequest request) {
        String date = request.getDate();
        log.info("Fetch NBA scores request for date={}", date);
        String url = String.format(NBA_API_URL_TEMPLATE, date, API_KEY);
        try {
            String rawJson = restTemplate.getForObject(new URI(url), String.class);
            if (rawJson == null || rawJson.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from NBA API");
            }
            JsonNode rootNode = objectMapper.readTree(rawJson);
            List<Game> gamesForDate = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    gamesForDate.add(parseGameFromJson(node, date));
                }
            } else {
                gamesForDate.add(parseGameFromJson(rootNode, date));
            }
            gamesByDate.put(date, gamesForDate);
            log.info("Stored {} games for date {}", gamesForDate.size(), date);
            return new MessageResponse("Scores fetched and stored successfully", null, date, gamesForDate.size(), null);
        } catch (Exception ex) {
            log.error("Error fetching or parsing NBA API data for date={}: {}", date, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch or parse NBA data");
        }
    }

    @PostMapping("/notifications/send")
    public MessageResponse sendNotifications(@RequestBody @Valid NotificationRequest request) {
        String date = request.getDate();
        log.info("Send notifications request for date={}", date);
        List<Game> games = gamesByDate.get(date);
        if (games == null || games.isEmpty()) {
            return new MessageResponse("No games found for date, notifications not sent", null, date, 0, 0);
        }
        Collection<Subscriber> currentSubscribers = subscribers.values();
        if (currentSubscribers.isEmpty()) {
            return new MessageResponse("No subscribers to notify", null, date, games.size(), 0);
        }
        CompletableFuture.runAsync(() -> {
            log.info("Sending notifications to {} subscribers for date {}", currentSubscribers.size(), date);
            for (Subscriber sub : currentSubscribers) {
                log.info("Sending email to {} with {} games summary for date {}", sub.getEmail(), games.size(), date);
            }
            log.info("Completed sending notifications for date {}", date);
        });
        return new MessageResponse("Notifications sending started", null, date, games.size(), currentSubscribers.size());
    }

    @GetMapping("/games/all")
    public List<Game> getAllGames() {
        log.info("Retrieving all stored games");
        List<Game> allGames = new ArrayList<>();
        gamesByDate.values().forEach(allGames::addAll);
        return allGames;
    }

    @GetMapping("/games/{date}")
    public List<Game> getGamesByDate(
        @PathVariable
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        String date) {
        log.info("Retrieving games for date {}", date);
        return gamesByDate.getOrDefault(date, Collections.emptyList());
    }

    private Game parseGameFromJson(JsonNode node, String date) {
        String gameId = node.hasNonNull("GameID") ? node.get("GameID").asText() : UUID.randomUUID().toString();
        String homeTeam = node.hasNonNull("HomeTeam") ? node.get("HomeTeam").asText() : "Unknown";
        String awayTeam = node.hasNonNull("AwayTeam") ? node.get("AwayTeam").asText() : "Unknown";
        Integer homeScore = node.hasNonNull("HomeTeamScore") ? node.get("HomeTeamScore").asInt() : null;
        Integer awayScore = node.hasNonNull("AwayTeamScore") ? node.get("AwayTeamScore").asInt() : null;
        return new Game(gameId, date, homeTeam, awayTeam, homeScore, awayScore);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getMessage());
        return Map.of(
            "error", ex.getReason(),
            "status", ex.getStatusCode().value()
        );
    }
}