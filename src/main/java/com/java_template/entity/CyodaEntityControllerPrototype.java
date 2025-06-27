package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.scheduling.annotation.Async;
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

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Received fetch-scores request for date={}", request.getDate());
        CompletableFuture.runAsync(() -> fetchStoreNotify(request.getDate()));
        return ResponseEntity.ok(new FetchScoresResponse("success", request.getDate(), -1));
    }

    @Async
    protected void fetchStoreNotify(String date) {
        logger.info("Starting fetchStoreNotify for date={}", date);
        try {
            String url = String.format(EXTERNAL_API_TEMPLATE, date, API_KEY);
            URI uri = new URI(url);
            String rawJson = restTemplate.getForObject(uri, String.class);
            if (rawJson == null || rawJson.isEmpty()) {
                logger.error("Empty response from external API for date {}", date);
                return;
            }
            JsonNode rootNode = objectMapper.readTree(rawJson);
            List<Game> games = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    games.add(parseGameFromJson(node));
                }
            } else {
                logger.warn("Unexpected JSON structure from external API for date {}", date);
            }
            // Store games using entityService
            if (!games.isEmpty()) {
                try {
                    CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                            ENTITY_NAME,
                            ENTITY_VERSION,
                            games
                    );
                    List<UUID> ids = idsFuture.get();
                    logger.info("Stored {} games with technicalIds {}", games.size(), ids);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to store games for date {}", date, e);
                }
            }
            sendNotifications(date, games);
        } catch (Exception e) {
            logger.error("Error during fetchStoreNotify for date " + date, e);
        }
    }

    private Game parseGameFromJson(JsonNode node) {
        String homeTeam = node.path("HomeTeam").asText("");
        String awayTeam = node.path("AwayTeam").asText("");
        int homeScore = node.path("HomeTeamScore").asInt(-1);
        int awayScore = node.path("AwayTeamScore").asInt(-1);
        String dateStr = node.path("Day").asText("");
        return new Game(dateStr, homeTeam, awayTeam, homeScore, awayScore);
    }

    private void sendNotifications(String date, List<Game> games) {
        StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
        if (games.isEmpty()) {
            summary.append("No games played on this day.");
        } else {
            for (Game g : games) {
                summary.append(String.format("%s %d - %d %s\n", g.getHomeTeam(), g.getHomeScore(), g.getAwayScore(), g.getAwayTeam()));
            }
        }
        List<Subscriber> subscribers = getAllSubscribers();
        for (Subscriber sub : subscribers) {
            logger.info("Sending email to {} with summary:\n{}", sub.getEmail(), summary);
        }
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) throws ExecutionException, InterruptedException {
        logger.info("Subscription request received for email={}", request.getEmail());
        // Check if already subscribed by email ignoring case
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "IEQUALS", request.getEmail()));
        CompletableFuture<List<Map<String, Object>>> existingSubsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition
        ).thenApply(arrayNode -> {
            List<Map<String, Object>> list = new ArrayList<>();
            arrayNode.forEach(jsonNode -> {
                Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
                list.add(map);
            });
            return list;
        });
        List<Map<String, Object>> existingSubs = existingSubsFuture.get();
        if (existingSubs.isEmpty()) {
            Subscriber newSub = new Subscriber(request.getEmail());
            CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, newSub);
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
        List<Subscriber> subscribers = getAllSubscribers();
        List<String> emails = subscribers.stream().map(Subscriber::getEmail).collect(Collectors.toList());
        return ResponseEntity.ok(new SubscribersResponse(emails));
    }

    private List<Subscriber> getAllSubscribers() throws ExecutionException, InterruptedException {
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = itemsFuture.get();
        List<Subscriber> subscribers = new ArrayList<>();
        arrayNode.forEach(jsonNode -> {
            Subscriber sub = objectMapper.convertValue(jsonNode, Subscriber.class);
            subscribers.add(sub);
        });
        return subscribers;
    }

    @GetMapping("/games/all")
    public ResponseEntity<GamesPageResponse> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) throws ExecutionException, InterruptedException {
        logger.info("Retrieving all games with pagination page={}, size={}", page, size);
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
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
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) throws ExecutionException, InterruptedException {
        logger.info("Retrieving games for date={}", date);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME, ENTITY_VERSION, condition);
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
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
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
    static class Subscriber {
        private String email;
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