package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String NBA_API_KEY = "test"; // TODO: secure storage
    private static final String NBA_API_URL_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + NBA_API_KEY;

    // Entity models
    private static final String SUBSCRIBER_ENTITY = "Subscriber";
    private static final String GAME_ENTITY = "Game";
    private static final String GAME_FETCH_REQUEST_ENTITY = "GameFetchRequest";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

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
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Invalid date format, expected YYYY-MM-DD")
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameFetchRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) throws ExecutionException, InterruptedException {
        String emailLower = request.getEmail().toLowerCase(Locale.ROOT);

        // Check if subscriber already exists by condition
        String condition = String.format("{\"email\":\"%s\"}", emailLower);
        CompletableFuture<ArrayNode> existingSubsFuture = entityService.getItemsByCondition(SUBSCRIBER_ENTITY, ENTITY_VERSION, condition);
        ArrayNode existingSubs = existingSubsFuture.get();

        if (existingSubs.size() > 0) {
            logger.info("Subscription attempt but email already subscribed: {}", emailLower);
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", emailLower));
        }

        Subscriber subscriber = new Subscriber(emailLower, new Date());
        CompletableFuture<UUID> idFuture = entityService.addItem(SUBSCRIBER_ENTITY, ENTITY_VERSION, subscriber);
        UUID id = idFuture.get(); // wait for completion

        logger.info("New subscription added with technicalId {}: {}", id, emailLower);
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", emailLower));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(SUBSCRIBER_ENTITY, ENTITY_VERSION);
        ArrayNode subscribersArray = itemsFuture.get();

        List<String> emails = new ArrayList<>();
        for (JsonNode node : subscribersArray) {
            String email = node.path("email").asText(null);
            if (email != null) {
                emails.add(email);
            }
        }
        logger.info("Retrieved {} subscribers", emails.size());
        return ResponseEntity.ok(new SubscribersResponse(emails));
    }

    @GetMapping("/games/all")
    public ResponseEntity<GamesResponse> getAllGames(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer size) throws ExecutionException, InterruptedException {

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(GAME_ENTITY, ENTITY_VERSION);
        ArrayNode gamesArray = itemsFuture.get();

        List<Game> allGames = new ArrayList<>();
        for (JsonNode node : gamesArray) {
            Game g = convertNodeToGame(node);
            allGames.add(g);
        }
        int total = allGames.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Game> pageGames = allGames.subList(fromIndex, toIndex);

        logger.info("Retrieved all games page {} size {} total {}", page, size, total);
        return ResponseEntity.ok(new GamesResponse(pageGames, page, size, total));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<GamesResponse> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Invalid date format, expected YYYY-MM-DD") String date) throws ExecutionException, InterruptedException {
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format requested: {}", date);
            throw new ResponseStatusException(400, "Invalid date format, expected YYYY-MM-DD");
        }

        String condition = String.format("{\"date\":\"%s\"}", date);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(GAME_ENTITY, ENTITY_VERSION, condition);
        ArrayNode gamesArray = filteredItemsFuture.get();

        List<Game> games = new ArrayList<>();
        for (JsonNode node : gamesArray) {
            games.add(convertNodeToGame(node));
        }

        logger.info("Retrieved {} games for date {}", games.size(), date);
        return ResponseEntity.ok(new GamesResponse(games, null, null, games.size()));
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchGamesResponse> fetchAndStoreScores(@RequestBody @Valid FetchGamesRequest request) throws ExecutionException, InterruptedException {
        String date = request.getDate();
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format for fetch: {}", date);
            throw new ResponseStatusException(400, "Invalid date format, expected YYYY-MM-DD");
        }

        GameFetchRequest fetchRequest = new GameFetchRequest(date);

        CompletableFuture<UUID> idFuture = entityService.addItem(GAME_FETCH_REQUEST_ENTITY, ENTITY_VERSION, fetchRequest);
        UUID id = idFuture.get();

        logger.info("Added GameFetchRequest entity with id {} for date {}", id, date);
        return ResponseEntity.ok(new FetchGamesResponse("Fetch request accepted and processing asynchronously", date, -1));
    }

    private Game parseGameFromJsonNode(JsonNode node, String date) {
        String homeTeam = node.path("HomeTeam").asText(null);
        String awayTeam = node.path("AwayTeam").asText(null);
        Integer homeScore = node.has("HomeTeamScore") && node.get("HomeTeamScore").canConvertToInt() ? node.get("HomeTeamScore").asInt() : null;
        Integer awayScore = node.has("AwayTeamScore") && node.get("AwayTeamScore").canConvertToInt() ? node.get("AwayTeamScore").asInt() : null;
        String otherInfo = node.toString();
        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, otherInfo);
    }

    private Game convertNodeToGame(JsonNode node) {
        String date = node.path("date").asText(null);
        String homeTeam = node.path("homeTeam").asText(null);
        String awayTeam = node.path("awayTeam").asText(null);
        Integer homeScore = node.path("homeScore").isInt() ? node.path("homeScore").asInt() : null;
        Integer awayScore = node.path("awayScore").isInt() ? node.path("awayScore").asInt() : null;
        String otherInfo = node.path("otherInfo").asText(null);
        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, otherInfo);
    }
}