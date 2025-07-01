Certainly! To follow the new recommended best practice, we should move all async and supplementary logic from the controller endpoints into dedicated `process{entity_name}` workflow functions. This will simplify the controllers, improve separation of concerns, and leverage the workflow mechanism for async and pre-persistence logic.

---

### Key points in the refactoring:

- **Move all async tasks (fetching external data, notifications, enrichment, etc.) into workflow functions**.
- The workflow function receives the entity data as an `ObjectNode`. We can modify it directly (`put`, `set`, etc.).
- Inside the workflow function, we **can** call `entityService` methods for different entityModels but **cannot** add/update/delete the same entityModel to avoid infinite recursion.
- The workflow is applied **before** the entity is persisted, so any modifications are saved automatically.
- Controllers only handle request/response, validation, and call `entityService.addItem` with the workflow.

---

### Refactoring plan for your code:

1. **Game entity: processGame(Object entityData)**
   - Move the logic of uppercasing names into this function.
   - Move the notification logic here (send emails to all subscribers).
   - Move external API fetching out of the controller — instead, the external data should be added as entities via another service or an external process; however, the current "fetch and notify" controller triggers this flow, so we will refactor accordingly.

2. **Subscriber entity: processSubscriber(Object entityData)**
   - If subscribers are stored as entities (instead of in-memory map), we can move subscribe-related logic here.

3. **Fetching external data and adding Game entities**
   - Since external fetching is async and adds multiple Game entities, and we cannot add/update/delete Game entities inside `processGame` (would cause recursion), we need a separate entityModel or a service outside this flow to add these Game entities.
   - Alternatively, create a new entityModel like `ExternalGameRaw` or `GameFetchRequest` that triggers the fetch + add flow in its workflow.
   - The controller then only adds this "fetch request" entity, which triggers fetching and adding Games inside the workflow.
   
---

### Given the above, here is the updated code illustrating these ideas.

---

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("cyoda-entity-prototype")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // --- Data classes ---

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SubscribeRequest {
        @NotBlank @Email
        private String email;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FetchAndNotifyRequest {
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date format must be YYYY-MM-DD")
        private String date;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FetchAndNotifyResponse {
        private String message;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SubscribersResponse {
        private List<String> subscribers;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GamesResponse {
        private List<Game> games;
        private Pagination pagination;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GamesByDateResponse {
        private String date;
        private List<Game> games;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Pagination {
        private int page;
        private int pageSize;
        private int totalPages;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Game {
        private Integer gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
    }

    // --- Controller endpoints simplified ---

    /**
     * Subscribe endpoint.
     * Now creates a Subscriber entity with workflow processSubscriber that handles duplicate check etc.
     */
    @PostMapping("/subscribe")
    public CompletableFuture<ResponseEntity<SubscribeResponse>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        ObjectNode subscriberEntity = objectMapper.createObjectNode();
        subscriberEntity.put("email", request.getEmail().toLowerCase(Locale.ROOT).trim());
        // Add the subscriber with workflow to handle async duplicate check or other logic
        return entityService.addItem("Subscriber", ENTITY_VERSION, subscriberEntity, this::processSubscriber)
                .thenApply(uuid -> ResponseEntity.ok(new SubscribeResponse("Subscription successful", request.getEmail())));
    }

    /**
     * Fetch and notify endpoint.
     * Now creates a FetchRequest entity that triggers fetch + add games + notify in the workflow.
     * Controller just adds the FetchRequest entity with workflow.
     */
    @PostMapping("/fetch-and-notify")
    public CompletableFuture<ResponseEntity<FetchAndNotifyResponse>> fetchAndNotify(@Valid @RequestBody FetchAndNotifyRequest request) {
        ObjectNode fetchRequestEntity = objectMapper.createObjectNode();
        fetchRequestEntity.put("date", request.getDate().trim());
        // Add a FetchRequest entity with workflow processFetchRequest that fetches games and adds them + notifies subscribers
        return entityService.addItem("FetchRequest", ENTITY_VERSION, fetchRequestEntity, this::processFetchRequest)
                .thenApply(uuid -> ResponseEntity.ok(new FetchAndNotifyResponse("Data fetching and notification started for date " + request.getDate())));
    }

    /**
     * List subscribers - now simply fetch all Subscriber entities.
     */
    @GetMapping("/subscribers")
    public CompletableFuture<ResponseEntity<SubscribersResponse>> getSubscribers() {
        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<String> emails = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        if (node.has("email")) {
                            emails.add(node.get("email").asText());
                        }
                    }
                    return ResponseEntity.ok(new SubscribersResponse(emails));
                });
    }

    /**
     * List all games with pagination.
     */
    @GetMapping("/games/all")
    public CompletableFuture<ResponseEntity<GamesResponse>> getAllGames(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int pageSize) {
        return entityService.getItems("Game", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Game> allGames = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        allGames.add(convertNodeToGame(node));
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
                    return ResponseEntity.ok(new GamesResponse(pagedGames, pagination));
                });
    }

    /**
     * Get games by date.
     */
    @GetMapping("/games/{date}")
    public CompletableFuture<ResponseEntity<GamesByDateResponse>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date format must be YYYY-MM-DD") String date) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));
        return entityService.getItemsByCondition("Game", ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    List<Game> games = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        games.add(convertNodeToGame(node));
                    }
                    return ResponseEntity.ok(new GamesByDateResponse(date, games));
                });
    }

    // --- Workflow functions ---

    /**
     * Workflow for Game entity.
     * Modify entity state before persistence.
     */
    private Object processGame(Object entityData) {
        if (!(entityData instanceof ObjectNode)) return entityData;
        ObjectNode gameNode = (ObjectNode) entityData;
        // Uppercase team names if present
        if (gameNode.has("homeTeam")) {
            gameNode.put("homeTeam", gameNode.get("homeTeam").asText().toUpperCase(Locale.ROOT));
        }
        if (gameNode.has("awayTeam")) {
            gameNode.put("awayTeam", gameNode.get("awayTeam").asText().toUpperCase(Locale.ROOT));
        }
        // Could add more processing here if needed
        // Notifications are moved to FetchRequest workflow (because we cannot add Game inside processGame)
        return gameNode;
    }

    /**
     * Workflow for Subscriber entity.
     * Here we can check for duplicate email asynchronously before persistence.
     * If duplicate found, throw exception to abort add.
     */
    private Object processSubscriber(Object entityData) {
        if (!(entityData instanceof ObjectNode)) return entityData;
        ObjectNode subscriberNode = (ObjectNode) entityData;
        String email = subscriberNode.has("email") ? subscriberNode.get("email").asText().toLowerCase(Locale.ROOT).trim() : null;
        if (email == null || email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        try {
            // Query existing subscribers with this email
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.email", "EQUALS", email));
            CompletableFuture<ArrayNode> existingFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition);
            ArrayNode existing = existingFuture.get();
            if (existing != null && !existing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already subscribed");
            }
        } catch (Exception e) {
            if (e instanceof ResponseStatusException) throw (ResponseStatusException) e;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error checking subscription", e);
        }
        // All good, proceed with persistence
        subscriberNode.put("email", email);
        subscriberNode.put("subscribedAt", Instant.now().toString());
        return subscriberNode;
    }

    /**
     * Workflow for FetchRequest entity.
     * This function asynchronously fetches external game data for the requested date,
     * adds Game entities (with processGame workflow),
     * and notifies all Subscribers asynchronously.
     * 
     * Important:
     * - We do NOT modify FetchRequest entity here (it is just a trigger).
     * - We use entityService.addItems to add multiple Game entities with processGame workflow.
     */
    private Object processFetchRequest(Object entityData) {
        if (!(entityData instanceof ObjectNode)) return entityData;
        ObjectNode fetchRequestNode = (ObjectNode) entityData;
        String date = fetchRequestNode.has("date") ? fetchRequestNode.get("date").asText() : null;
        if (date == null || date.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is required for fetch request");
        }

        // Run async task (fire and forget)
        CompletableFuture.runAsync(() -> {
            try {
                fetchGamesAndNotifySubscribers(date);
            } catch (Exception e) {
                logger.error("Error in fetchGamesAndNotifySubscribers for date {}: {}", date, e.getMessage(), e);
            }
        });

        // Return entity unchanged
        return entityData;
    }

    // --- Helper methods ---

    /**
     * Fetch external NBA game data for the date,
     * add Game entities with processGame workflow,
     * and notify Subscribers.
     */
    private void fetchGamesAndNotifySubscribers(String date) throws Exception {
        logger.info("Starting fetchGamesAndNotifySubscribers for date {}", date);

        // External API URL and key - adjust accordingly
        final String EXTERNAL_API_KEY = "test"; // TODO: secure config
        final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

        String url = String.format(EXTERNAL_API_URL_TEMPLATE,
                URLEncoder.encode(date, StandardCharsets.UTF_8),
                URLEncoder.encode(EXTERNAL_API_KEY, StandardCharsets.UTF_8));

        RestTemplate restTemplate = new RestTemplate();
        String rawJson = restTemplate.getForObject(URI.create(url), String.class);
        if (rawJson == null) {
            logger.warn("No data received from external API for date {}", date);
            return;
        }

        JsonNode rootNode = objectMapper.readTree(rawJson);
        if (!rootNode.isArray()) {
            logger.warn("Unexpected external API response format for date {}", date);
            return;
        }

        List<ObjectNode> gameEntities = new ArrayList<>();
        for (JsonNode gameNode : rootNode) {
            ObjectNode gameEntity = objectMapper.createObjectNode();
            gameEntity.put("gameId", gameNode.path("GameID").asInt(0));
            gameEntity.put("date", gameNode.path("Day").asText(date));
            gameEntity.put("homeTeam", gameNode.path("HomeTeam").asText("N/A"));
            gameEntity.put("awayTeam", gameNode.path("AwayTeam").asText("N/A"));
            if (gameNode.has("HomeTeamScore") && gameNode.get("HomeTeamScore").isInt()) {
                gameEntity.put("homeScore", gameNode.get("HomeTeamScore").asInt());
            }
            if (gameNode.has("AwayTeamScore") && gameNode.get("AwayTeamScore").isInt()) {
                gameEntity.put("awayScore", gameNode.get("AwayTeamScore").asInt());
            }
            gameEntities.add(gameEntity);
        }

        // Add all Game entities with processGame workflow
        entityService.addItems("Game", ENTITY_VERSION, gameEntities, this::processGame).get();

        logger.info("Added {} Game entities for date {}", gameEntities.size(), date);

        // Notify all subscribers asynchronously
        notifyAllSubscribers(date, gameEntities);
    }

    /**
     * Send notifications to all subscribers.
     * This is done asynchronously - ideally integrate with real email service.
     */
    private void notifyAllSubscribers(String date, List<ObjectNode> games) {
        entityService.getItems("Subscriber", ENTITY_VERSION).thenAccept(subscribersArray -> {
            if (subscribersArray.isEmpty()) {
                logger.info("No subscribers to notify for date {}", date);
                return;
            }
            StringBuilder emailContent = new StringBuilder();
            emailContent.append("Daily NBA Scores for ").append(date).append(":\n\n");
            for (ObjectNode game : games) {
                String homeTeam = game.has("homeTeam") ? game.get("homeTeam").asText() : "N/A";
                String awayTeam = game.has("awayTeam") ? game.get("awayTeam").asText() : "N/A";
                int homeScore = game.has("homeScore") ? game.get("homeScore").asInt() : 0;
                int awayScore = game.has("awayScore") ? game.get("awayScore").asInt() : 0;
                emailContent.append(String.format("%s vs %s: %d - %d%n", homeTeam, awayTeam, homeScore, awayScore));
            }
            subscribersArray.forEach(subscriberNode -> {
                String email = subscriberNode.has("email") ? subscriberNode.get("email").asText() : null;
                if (email != null && !email.isEmpty()) {
                    CompletableFuture.runAsync(() -> {
                        logger.info("Sending email to {} with content:\n{}", email, emailContent);
                        // TODO: integrate with real email service here
                    });
                }
            });
        }).exceptionally(ex -> {
            logger.error("Failed to retrieve subscribers for notification: {}", ex.getMessage(), ex);
            return null;
        });
    }

    // --- Utility ---

    private Game convertNodeToGame(JsonNode node) {
        Integer gameId = node.has("gameId") && node.get("gameId").isInt() ? node.get("gameId").asInt() : null;
        String date = node.has("date") ? node.get("date").asText() : null;
        String homeTeam = node.has("homeTeam") ? node.get("homeTeam").asText() : null;
        String awayTeam = node.has("awayTeam") ? node.get("awayTeam").asText() : null;
        Integer homeScore = node.has("homeScore") && node.get("homeScore").isInt() ? node.get("homeScore").asInt() : null;
        Integer awayScore = node.has("awayScore") && node.get("awayScore").isInt() ? node.get("awayScore").asInt() : null;
        return new Game(gameId, date, homeTeam, awayTeam, homeScore, awayScore);
    }

    // --- Exception handlers omitted for brevity (can be same as before) ---
}
```

---

### Summary:

- **`subscribe` endpoint**: just creates a `Subscriber` entity with `processSubscriber` workflow that does duplicate check and timestamps.
- **`fetch-and-notify` endpoint**: creates a `FetchRequest` entity with `processFetchRequest` workflow that fetches external data, adds `Game` entities with `processGame` workflow, and sends notifications asynchronously.
- **`processGame` workflow**: does simple transformation (uppercase team names).
- **`processSubscriber` workflow**: checks for duplicate subscriptions asynchronously before save.
- **`processFetchRequest` workflow**: runs async fetch and notify task, does NOT mutate the `FetchRequest` entity.
- Controllers are streamlined, no async logic inside them.
- All async and entity-related logic is inside workflow functions, executed before persistence.

---

This approach fully embraces your new architecture and makes the system more robust, maintainable, and testable. The controllers are clean and focused on request/response only.

If you want, I can also provide exception handlers and other minor improvements for completeness.