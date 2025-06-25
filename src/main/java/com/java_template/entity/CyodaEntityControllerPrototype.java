package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String NBA_API_KEY = "test"; // TODO: secure storage
    private static final String NBA_API_URL_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + NBA_API_KEY;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
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

    // Use entityService to store subscribers under "Subscriber" entityModel
    private static final String SUBSCRIBER_ENTITY = "Subscriber";

    // Use entityService to store games under "Game" entityModel
    private static final String GAME_ENTITY = "Game";

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
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

            // delete existing games for the date first to avoid duplicates
            String condition = String.format("{\"date\":\"%s\"}", date);
            CompletableFuture<ArrayNode> existingGamesFuture = entityService.getItemsByCondition(GAME_ENTITY, ENTITY_VERSION, condition);
            ArrayNode existingGames = existingGamesFuture.get();

            for (JsonNode existingGameNode : existingGames) {
                UUID technicalId = UUID.fromString(existingGameNode.path("technicalId").asText());
                entityService.deleteItem(GAME_ENTITY, ENTITY_VERSION, technicalId).get();
            }

            // add fetched games
            CompletableFuture<List<UUID>> addItemsFuture = entityService.addItems(GAME_ENTITY, ENTITY_VERSION, fetchedGames);
            List<UUID> addedIds = addItemsFuture.get();

            logger.info("Stored {} games for date {}", addedIds.size(), date);
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

    private Game convertNodeToGame(JsonNode node) {
        String date = node.path("date").asText(null);
        String homeTeam = node.path("homeTeam").asText(null);
        String awayTeam = node.path("awayTeam").asText(null);
        Integer homeScore = node.path("homeScore").isInt() ? node.path("homeScore").asInt() : null;
        Integer awayScore = node.path("awayScore").isInt() ? node.path("awayScore").asInt() : null;
        String otherInfo = node.path("otherInfo").asText(null);
        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, otherInfo);
    }

    private void notifySubscribers(String date, List<Game> games) throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(SUBSCRIBER_ENTITY, ENTITY_VERSION);
        ArrayNode subscribersArray = subscribersFuture.get();

        List<String> emails = new ArrayList<>();
        for (JsonNode node : subscribersArray) {
            String email = node.path("email").asText(null);
            if (email != null) {
                emails.add(email);
            }
        }

        if (emails.isEmpty()) {
            logger.info("No subscribers to notify for date {}", date);
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("NBA Scores for ").append(date).append(":\n");
        for (Game g : games) {
            summary.append(String.format("%s vs %s: %d - %d\n",
                    g.getHomeTeam(), g.getAwayTeam(),
                    Optional.ofNullable(g.getHomeScore()).orElse(-1),
                    Optional.ofNullable(g.getAwayScore()).orElse(-1)));
        }

        for (String email : emails) {
            logger.info("Sending email to {}: \n{}", email, summary);
            // TODO: integrate real email sending
        }
        logger.info("Email notifications sent to {} subscribers for date {}", emails.size(), date);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", ex.getStatusCode().value());
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}