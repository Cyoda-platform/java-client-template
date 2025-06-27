package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";
    private static final String ENTITY_NAME = "prototype";
    private static final int ENTITY_VERSION = 1; // Replace with actual constant

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    private CompletableFuture<ObjectNode> processprototype(ObjectNode entity) {
        String action = entity.has("action") ? entity.get("action").asText() : "";

        if ("fetchScores".equals(action)) {
            return processFetchScores(entity);
        } else if ("subscribe".equals(action)) {
            return CompletableFuture.completedFuture(entity);
        } else {
            return CompletableFuture.completedFuture(entity);
        }
    }

    private CompletableFuture<ObjectNode> processFetchScores(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!entity.hasNonNull("date")) {
                    logger.warn("processFetchScores: missing date field");
                    entity.put("gamesCount", 0);
                    entity.put("error", "Missing date");
                    return entity;
                }

                String date = entity.get("date").asText();
                logger.info("processprototype: fetching scores for date={}", date);

                String url = String.format(EXTERNAL_API_TEMPLATE, date, API_KEY);
                URI uri = new URI(url);
                String rawJson = restTemplate.getForObject(uri, String.class);
                if (rawJson == null || rawJson.isEmpty()) {
                    logger.warn("Empty response from external API for date {}", date);
                    entity.put("gamesCount", 0);
                    entity.put("error", "Empty response from external API");
                    return entity;
                }

                JsonNode rootNode = objectMapper.readTree(rawJson);
                List<ObjectNode> games = new ArrayList<>();

                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        ObjectNode gameNode = parseGameToObjectNode(node);
                        games.add(gameNode);
                    }
                } else {
                    logger.warn("Unexpected JSON structure from external API for date {}", date);
                }

                for (ObjectNode gameNode : games) {
                    entityService.addItem(
                            "game",
                            ENTITY_VERSION,
                            gameNode
                    );
                }

                entity.put("gamesCount", games.size());

                List<String> subscriberEmails = getSubscriberEmailsSync();

                StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
                if (games.isEmpty()) {
                    summary.append("No games played on this day.");
                } else {
                    for (ObjectNode g : games) {
                        String homeTeam = g.path("homeTeam").asText();
                        String awayTeam = g.path("awayTeam").asText();
                        int homeScore = g.path("homeScore").asInt(-1);
                        int awayScore = g.path("awayScore").asInt(-1);
                        summary.append(String.format("%s %d - %d %s\n", homeTeam, homeScore, awayScore, awayTeam));
                    }
                }

                for (String email : subscriberEmails) {
                    ObjectNode notification = objectMapper.createObjectNode();
                    notification.put("email", email);
                    notification.put("subject", "NBA Scores Notification");
                    notification.put("body", summary.toString());
                    notification.put("date", date);

                    entityService.addItem(
                            "notification",
                            ENTITY_VERSION,
                            notification
                    );
                    logger.info("Queued notification for email {}", email);
                }

                return entity;
            } catch (Exception e) {
                logger.error("Error in processFetchScores workflow", e);
                entity.put("error", "Failed to fetch or process scores");
                return entity;
            }
        });
    }

    private ObjectNode parseGameToObjectNode(JsonNode node) {
        ObjectNode gameNode = objectMapper.createObjectNode();
        gameNode.put("date", node.path("Day").asText(""));
        gameNode.put("homeTeam", node.path("HomeTeam").asText(""));
        gameNode.put("awayTeam", node.path("AwayTeam").asText(""));
        gameNode.put("homeScore", node.path("HomeTeamScore").asInt(-1));
        gameNode.put("awayScore", node.path("AwayTeamScore").asInt(-1));
        return gameNode;
    }

    private List<String> getSubscriberEmailsSync() throws ExecutionException, InterruptedException {
        SearchConditionRequest cond = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EXISTS", null));
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> future = entityService.getItemsByCondition(
                ENTITY_NAME, ENTITY_VERSION, cond);
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = future.get();
        List<String> emails = new ArrayList<>();
        arrayNode.forEach(jsonNode -> {
            if (jsonNode.has("email")) {
                emails.add(jsonNode.get("email").asText());
            }
        });
        return emails;
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Received fetch-scores request for date={}", request.getDate());

        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("action", "fetchScores");
        entityNode.put("date", request.getDate());

        entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entityNode
        );

        return ResponseEntity.ok(new FetchScoresResponse("success", request.getDate(), -1));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) throws ExecutionException, InterruptedException {
        logger.info("Subscription request received for email={}", request.getEmail());

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "IEQUALS", request.getEmail()));
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> existingSubsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition
        );
        com.fasterxml.jackson.databind.node.ArrayNode existingSubs = existingSubsFuture.get();

        if (existingSubs.isEmpty()) {
            ObjectNode entityNode = objectMapper.createObjectNode();
            entityNode.put("email", request.getEmail());
            entityNode.put("action", "subscribe");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    entityNode
            );
            idFuture.get();
            logger.info("Email {} subscribed successfully", request.getEmail());
        } else {
            logger.info("Email {} already subscribed", request.getEmail());
        }
        return ResponseEntity.ok(new SubscribeResponse("subscribed", request.getEmail()));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() throws ExecutionException, InterruptedException {
        logger.info("Retrieving all subscribers");
        List<String> emails = getSubscriberEmailsSync();
        return ResponseEntity.ok(new SubscribersResponse(emails));
    }

    @GetMapping("/games/all")
    public ResponseEntity<GamesPageResponse> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) throws ExecutionException, InterruptedException {
        logger.info("Retrieving all games with pagination page={}, size={}", page, size);
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItems("game", ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = itemsFuture.get();
        List<Game> allGames = new ArrayList<>();
        arrayNode.forEach(jsonNode -> {
            Game game = objectMapper.convertValue(jsonNode, Game.class);
            allGames.add(game);
        });
        int total = allGames.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<Game> content = allGames.subList(from, to);
        int totalPages = (total + size - 1) / size;
        return ResponseEntity.ok(new GamesPageResponse(page, size, totalPages, content));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<GamesByDateResponse> getGamesByDate(
            @PathVariable @Pattern(regexp = "\d{4}-\d{2}-\d{2}") String date) throws ExecutionException, InterruptedException {
        logger.info("Retrieving games for date={}", date);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                "game", ENTITY_VERSION, condition);
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = filteredItemsFuture.get();
        List<Game> games = new ArrayList<>();
        arrayNode.forEach(jsonNode -> {
            Game game = objectMapper.convertValue(jsonNode, Game.class);
            games.add(game);
        });
        return ResponseEntity.ok(new GamesByDateResponse(date, games));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("status", ex.getStatusCode().toString());
        logger.error("Handled error: {}, status: {}", ex.getStatusCode(), ex.getStatusCode());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("status", "500 INTERNAL_SERVER_ERROR");
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(error);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        @NotNull
        @Pattern(regexp = "\d{4}-\d{2}-\d{2}")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresResponse {
        private String status;
        private String fetchedDate;
        private int gamesCount;
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
        private String status;
        private String email;
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
    static class GamesPageResponse {
        private int page;
        private int size;
        private int totalPages;
        private List<Game> games;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GamesByDateResponse {
        private String date;
        private List<Game> games;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
    }
}