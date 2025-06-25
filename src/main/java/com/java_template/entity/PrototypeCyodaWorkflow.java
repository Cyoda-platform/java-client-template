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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

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

    // Entity models
    private static final String SUBSCRIBER_ENTITY = "Subscriber";
    private static final String GAME_ENTITY = "Game";
    private static final String GAME_FETCH_REQUEST_ENTITY = "GameFetchRequest";

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
        CompletableFuture<UUID> idFuture = entityService.addItem(SUBSCRIBER_ENTITY, ENTITY_VERSION, subscriber, processSubscriber);
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

        // Add GameFetchRequest entity, workflow will do async fetch, store, notify
        CompletableFuture<UUID> idFuture = entityService.addItem(GAME_FETCH_REQUEST_ENTITY, ENTITY_VERSION, fetchRequest, processGameFetchRequest);
        UUID id = idFuture.get();

        logger.info("Added GameFetchRequest entity with id {} for date {}", id, date);
        return ResponseEntity.ok(new FetchGamesResponse("Fetch request accepted and processing asynchronously", date, -1));
    }

    // --- Workflow functions ---

    private final Function<Object, Object> processSubscriber = entity -> {
        if (!(entity instanceof ObjectNode)) return entity;
        ObjectNode objectNode = (ObjectNode) entity;

        if (objectNode.has("email")) {
            String email = objectNode.get("email").asText().toLowerCase(Locale.ROOT);
            objectNode.put("email", email);
        }
        if (!objectNode.has("subscribedAt")) {
            objectNode.put("subscribedAt", System.currentTimeMillis());
        }
        return objectNode;
    };

    private final Function<Object, Object> processGame = entity -> {
        if (!(entity instanceof ObjectNode)) return entity;
        ObjectNode objectNode = (ObjectNode) entity;

        if (objectNode.has("date")) {
            String dateStr = objectNode.get("date").asText();
            try {
                LocalDate date = LocalDate.parse(dateStr);
                objectNode.put("date", date.toString());
            } catch (DateTimeParseException ignored) {
            }
        }
        return objectNode;
    };

    private final Function<Object, Object> processGameFetchRequest = entity -> {
        if (!(entity instanceof ObjectNode)) return entity;
        ObjectNode fetchRequestNode = (ObjectNode) entity;
        try {
            if (!fetchRequestNode.has("date")) {
                logger.warn("GameFetchRequest entity missing 'date' field");
                return entity;
            }
            String date = fetchRequestNode.get("date").asText();

            logger.info("Processing GameFetchRequest workflow for date {}", date);

            String url = String.format(NBA_API_URL_TEMPLATE, date);
            String rawJson = restTemplate.getForObject(url, String.class);
            if (rawJson == null || rawJson.isBlank()) {
                logger.warn("Empty response from NBA API for date {}", date);
                return entity;
            }
            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                logger.error("Unexpected NBA API response format for date {}: expected JSON array", date);
                return entity;
            }

            String condition = String.format("{\"date\":\"%s\"}", date);
            CompletableFuture<ArrayNode> existingGamesFuture = entityService.getItemsByCondition(GAME_ENTITY, ENTITY_VERSION, condition);
            ArrayNode existingGames = existingGamesFuture.get();

            for (JsonNode existingGameNode : existingGames) {
                if (existingGameNode.has("technicalId")) {
                    UUID technicalId = UUID.fromString(existingGameNode.get("technicalId").asText());
                    entityService.deleteItem(GAME_ENTITY, ENTITY_VERSION, technicalId).get();
                }
            }

            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (JsonNode gameNode : rootNode) {
                Game game = parseGameFromJsonNode(gameNode, date);
                futures.add(entityService.addItem(GAME_ENTITY, ENTITY_VERSION, game, processGame));
            }
            for (CompletableFuture<UUID> f : futures) f.get();

            logger.info("Stored {} games for date {}", futures.size(), date);

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(SUBSCRIBER_ENTITY, ENTITY_VERSION);
            ArrayNode subscribersArray = subscribersFuture.get();

            if (subscribersArray.size() == 0) {
                logger.info("No subscribers to notify for date {}", date);
                return entity;
            }

            StringBuilder summary = new StringBuilder();
            summary.append("NBA Scores for ").append(date).append(":\n");
            for (JsonNode gameNode : rootNode) {
                String homeTeam = gameNode.path("HomeTeam").asText("Unknown");
                String awayTeam = gameNode.path("AwayTeam").asText("Unknown");
                int homeScore = gameNode.path("HomeTeamScore").asInt(-1);
                int awayScore = gameNode.path("AwayTeamScore").asInt(-1);
                summary.append(String.format("%s vs %s: %d - %d\n", homeTeam, awayTeam, homeScore, awayScore));
            }

            for (JsonNode subscriberNode : subscribersArray) {
                String email = subscriberNode.path("email").asText(null);
                if (email != null) {
                    logger.info("Sending email to {}: \n{}", email, summary);
                    // TODO: integrate real email sending
                }
            }

            logger.info("Email notifications sent to {} subscribers for date {}", subscribersArray.size(), date);

        } catch (Exception e) {
            logger.error("Error in processGameFetchRequest workflow", e);
        }
        return entity;
    };

    // --- Helper methods ---

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