package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private static final String API_KEY = "test"; // TODO: replace with real API key or config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private final EntityService entityService;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class NotificationRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;
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
        logger.info("Subscribe request received for email={}", request.getEmail());
        Subscriber subscriber = new Subscriber(request.getEmail());
        try {
            entityService.addItem("Subscriber", ENTITY_VERSION, subscriber).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error adding subscriber: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add subscriber");
        }
        return new MessageResponse("Subscription successful", request.getEmail(), null, null, null);
    }

    @GetMapping("/subscribers")
    public List<String> getSubscribers() throws ExecutionException, InterruptedException {
        logger.info("Retrieving all subscribers");
        ArrayNode items = entityService.getItems("Subscriber", ENTITY_VERSION).get();
        List<String> emails = new ArrayList<>();
        for (JsonNode itemNode : items) {
            String email = itemNode.path("email").asText(null);
            if (email != null) {
                emails.add(email);
            }
        }
        return emails;
    }

    @PostMapping("/games/fetch")
    public MessageResponse fetchAndStoreScores(@RequestBody @Valid FetchRequest request) throws Exception {
        String date = request.getDate();
        logger.info("Fetch NBA scores request for date={}", date);
        String url = String.format(NBA_API_URL_TEMPLATE, date, API_KEY);
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

        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (Game game : gamesForDate) {
            futures.add(entityService.addItem("Game", ENTITY_VERSION, game));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        logger.info("Stored {} games for date {}", gamesForDate.size(), date);
        return new MessageResponse("Scores fetched and stored successfully", null, date, gamesForDate.size(), null);
    }

    @PostMapping("/notifications/send")
    public MessageResponse sendNotifications(@RequestBody @Valid NotificationRequest request) {
        logger.info("Send notifications request for date={}", request.getDate());

        try {
            entityService.addItem("NotificationRequest", ENTITY_VERSION, request).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error adding NotificationRequest: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to initiate notifications");
        }

        return new MessageResponse("Notifications sending initiated", null, request.getDate(), null, null);
    }

    @GetMapping("/games/all")
    public List<Game> getAllGames() throws ExecutionException, InterruptedException {
        logger.info("Retrieving all stored games");
        ArrayNode items = entityService.getItems("Game", ENTITY_VERSION).get();
        List<Game> games = new ArrayList<>();
        for (JsonNode node : items) {
            games.add(convertJsonNodeToGame(node));
        }
        return games;
    }

    @GetMapping("/games/{date}")
    public List<Game> getGamesByDate(
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
            String date) throws ExecutionException, InterruptedException {
        logger.info("Retrieving games for date {}", date);
        String condition = String.format("{\"date\":\"%s\"}", date);
        ArrayNode items = entityService.getItemsByCondition("Game", ENTITY_VERSION, condition).get();
        List<Game> games = new ArrayList<>();
        for (JsonNode node : items) {
            games.add(convertJsonNodeToGame(node));
        }
        return games;
    }

    private Game parseGameFromJson(JsonNode node, String date) {
        String gameId = node.hasNonNull("GameID") ? node.get("GameID").asText() : UUID.randomUUID().toString();
        String homeTeam = node.hasNonNull("HomeTeam") ? node.get("HomeTeam").asText() : "Unknown";
        String awayTeam = node.hasNonNull("AwayTeam") ? node.get("AwayTeam").asText() : "Unknown";
        Integer homeScore = node.hasNonNull("HomeTeamScore") ? node.get("HomeTeamScore").asInt() : null;
        Integer awayScore = node.hasNonNull("AwayTeamScore") ? node.get("AwayTeamScore").asInt() : null;
        return new Game(null, gameId, date, homeTeam, awayTeam, homeScore, awayScore);
    }

    private Game convertJsonNodeToGame(JsonNode node) {
        UUID technicalId = null;
        if (node.hasNonNull("technicalId")) {
            try {
                technicalId = UUID.fromString(node.get("technicalId").asText());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String gameId = node.hasNonNull("gameId") ? node.get("gameId").asText() : null;
        String date = node.hasNonNull("date") ? node.get("date").asText() : null;
        String homeTeam = node.hasNonNull("homeTeam") ? node.get("homeTeam").asText() : null;
        String awayTeam = node.hasNonNull("awayTeam") ? node.get("awayTeam").asText() : null;
        Integer homeScore = node.hasNonNull("homeScore") ? node.get("homeScore").asInt() : null;
        Integer awayScore = node.hasNonNull("awayScore") ? node.get("awayScore").asInt() : null;
        return new Game(technicalId, gameId, date, homeTeam, awayTeam, homeScore, awayScore);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        return Map.of(
                "error", ex.getStatusCode().toString(),
                "status", ex.getStatusCode().value()
        );
    }
}