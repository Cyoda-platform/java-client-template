package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("cyoda-entity-prototype")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchAndNotifyRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date format must be YYYY-MM-DD")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchAndNotifyResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribersResponse {
        private List<String> subscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesResponse {
        private List<Game> games;
        private Pagination pagination;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesByDateResponse {
        private String date;
        private List<Game> games;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        private int page;
        private int pageSize;
        private int totalPages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Game {
        private Integer gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
    }

    // Use local in-memory subscriberStore as no suitable entityService method to manage subscribers
    private final Map<String, Instant> subscriberStore = new ConcurrentHashMap<>();

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT).trim();
        if (subscriberStore.containsKey(email)) {
            logger.info("Subscription attempt with already subscribed email: {}", email);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already subscribed");
        }
        subscriberStore.put(email, Instant.now());
        logger.info("New subscription added for email: {}", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    @PostMapping("/fetch-and-notify")
    public ResponseEntity<FetchAndNotifyResponse> fetchAndNotify(@Valid @RequestBody FetchAndNotifyRequest request) {
        String date = request.getDate().trim();
        logger.info("Received request to fetch and notify for date: {}", date);
        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(date);
            } catch (Exception e) {
                logger.error("Error during fetch and notify for date {}: {}", date, e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(new FetchAndNotifyResponse("Data fetching and notification started for date " + date));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        List<String> emails = new ArrayList<>(subscriberStore.keySet());
        logger.info("Returning {} subscribers", emails.size());
        return ResponseEntity.ok(new SubscribersResponse(emails));
    }

    @GetMapping("/games/all")
    public ResponseEntity<GamesResponse> getAllGames(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int pageSize) throws Exception {
        // Retrieve all games via entityService.getItems
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("Game", ENTITY_VERSION);
        ArrayNode items = itemsFuture.get(); // blocking for simplicity, could be async if needed

        List<Game> allGames = new ArrayList<>();
        for (JsonNode node : items) {
            Game g = convertNodeToGame(node);
            allGames.add(g);
        }
        allGames.sort(Comparator.comparing(Game::getDate, Comparator.reverseOrder()));

        int total = allGames.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        if (page > totalPages) {
            page = 1;
        }
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Game> pagedGames = allGames.subList(fromIndex, toIndex);
        Pagination pagination = new Pagination(page, pageSize, totalPages);
        logger.info("Returning games page {} of {}, {} items", page, totalPages, pagedGames.size());
        return ResponseEntity.ok(new GamesResponse(pagedGames, pagination));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<GamesByDateResponse> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date format must be YYYY-MM-DD") String date) throws Exception {
        // Create condition for date equality
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Game", ENTITY_VERSION, condition);
        ArrayNode filteredItems = filteredItemsFuture.get();

        List<Game> games = new ArrayList<>();
        for (JsonNode node : filteredItems) {
            games.add(convertNodeToGame(node));
        }
        logger.info("Returning {} games for date {}", games.size(), date);
        return ResponseEntity.ok(new GamesByDateResponse(date, games));
    }

    private void fetchStoreAndNotify(String date) throws Exception {
        logger.info("Start fetchStoreAndNotify for date {}", date);
        String url = String.format(EXTERNAL_API_URL_TEMPLATE,
                URLEncoder.encode(date, StandardCharsets.UTF_8),
                URLEncoder.encode(EXTERNAL_API_KEY, StandardCharsets.UTF_8));
        logger.info("Fetching external NBA scores from URL: {}", url);
        String rawJson = restTemplate.getForObject(URI.create(url), String.class);
        if (rawJson == null) {
            logger.error("No data returned from external API for date {}", date);
            return;
        }
        JsonNode rootNode = objectMapper.readTree(rawJson);
        if (!rootNode.isArray()) {
            logger.error("Unexpected data format from external API, expected array");
            return;
        }
        List<Game> fetchedGames = new ArrayList<>();
        for (JsonNode node : rootNode) {
            Integer gameId = node.has("GameID") && node.get("GameID").isInt() ? node.get("GameID").asInt() : null;
            String gameDate = node.has("Day") ? node.get("Day").asText() : date;
            String homeTeam = node.has("HomeTeam") ? node.get("HomeTeam").asText() : "N/A";
            String awayTeam = node.has("AwayTeam") ? node.get("AwayTeam").asText() : "N/A";
            Integer homeScore = node.has("HomeTeamScore") && node.get("HomeTeamScore").isInt() ? node.get("HomeTeamScore").asInt() : null;
            Integer awayScore = node.has("AwayTeamScore") && node.get("AwayTeamScore").isInt() ? node.get("AwayTeamScore").asInt() : null;
            fetchedGames.add(new Game(gameId, gameDate, homeTeam, awayTeam, homeScore, awayScore));
        }

        // Store fetched games via entityService.addItems
        List<Object> gamesToAdd = new ArrayList<>();
        for (Game g : fetchedGames) {
            gamesToAdd.add(g);
        }
        entityService.addItems("Game", ENTITY_VERSION, gamesToAdd).get();

        logger.info("Stored {} games for date {}", fetchedGames.size(), date);
        notifySubscribers(date, fetchedGames);
    }

    private void notifySubscribers(String date, List<Game> games) {
        logger.info("Preparing to notify {} subscribers for date {}", subscriberStore.size(), date);
        if (subscriberStore.isEmpty()) {
            logger.info("No subscribers to notify.");
            return;
        }
        StringBuilder emailContent = new StringBuilder();
        emailContent.append("Daily NBA Scores for ").append(date).append(":\n\n");
        for (Game g : games) {
            emailContent.append(String.format("%s vs %s: %d - %d%n",
                    g.getHomeTeam(), g.getAwayTeam(),
                    Optional.ofNullable(g.getHomeScore()).orElse(0),
                    Optional.ofNullable(g.getAwayScore()).orElse(0)));
        }
        subscriberStore.keySet().forEach(email -> {
            CompletableFuture.runAsync(() -> {
                logger.info("Sending email to {} with content:\n{}", email, emailContent.toString());
                // TODO: Integrate with real email sending service
            });
        });
    }

    private Game convertNodeToGame(JsonNode node) {
        Integer gameId = node.has("gameId") && node.get("gameId").isInt() ? node.get("gameId").asInt() : null;
        String date = node.has("date") ? node.get("date").asText() : null;
        String homeTeam = node.has("homeTeam") ? node.get("homeTeam").asText() : null;
        String awayTeam = node.has("awayTeam") ? node.get("awayTeam").asText() : null;
        Integer homeScore = node.has("homeScore") && node.get("homeScore").isInt() ? node.get("homeScore").asInt() : null;
        Integer awayScore = node.has("awayScore") && node.get("awayScore").isInt() ? node.get("awayScore").asInt() : null;
        return new Game(gameId, date, homeTeam, awayTeam, homeScore, awayScore);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("API error: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}