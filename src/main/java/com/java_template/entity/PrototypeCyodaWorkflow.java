Certainly! This is a great architectural improvement: moving asynchronous logic and side effects from controllers into the workflow functions (`process{entity_name}`), which are invoked right before persistence, keeps controllers thin and focuses workflows on entity state mutation and related async tasks.

---

### What to move into workflow functions?

1. **For `subscriber` entity**:
   - Currently, subscription just adds subscriber.
   - No async side effects like notifications yet, so minimal logic.
   - We can keep validations in controller; workflow can be used for possible future async enrichments.

2. **For `game` entity**:
   - The current `fetchStoreAndNotify` method:
     - Fetches external data,
     - Saves games,
     - Sends notifications to subscribers.

   - The fetching and notification are async tasks related to games.
   - We **cannot** add/update/delete `game` entities inside `processGame` (would cause recursion).
   - But we **can**:
     - Modify the entity,
     - Add supplementary entities of other models (e.g., logs, notifications),
     - Fire async tasks,
     - Retrieve other entities (e.g., subscribers),
     - Send notifications.

---

### Approach:

- **Move notification sending into `processGame` workflow**:
  - When a game entity is about to be persisted, `processGame` will fetch subscribers and send notifications.
  - This decouples notification from controller.
- **Fetch external data**:
  - This is a controller-level trigger (`fetchAndNotify`), so **cannot move fetch into `processGame`** because `processGame` receives only a single entity and does not trigger bulk inserts.
  - The controller still triggers fetch, then inserts games **one-by-one** with workflow attached.
- **Change `fetchAndNotify` controller**:
  - Fetch external data,
  - For each game in fetched data, call `entityService.addItem(…, game, processGame)`.
- **In `processGame`**:
  - Send notification about the particular game asynchronously (fire-and-forget).
  - Modify game entity if needed.
- **In `processSubscriber`**:
  - No changes for now.

---

### Important:

- `processGame` must **not** invoke `entityService.addItem` on `game` again (infinite recursion).
- Can invoke `entityService` methods for other entity models (e.g., logs).
- The entity parameter is an `ObjectNode` (Jackson JSON tree), so we modify it directly.

---

### Updated code:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("cyoda-prototype")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + API_KEY;

    private static final String ENTITY_NAME_SUBSCRIBER = "subscriber";
    private static final String ENTITY_NAME_GAME = "game";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = entityService.getObjectMapper(); // reuse mapper from service if available
    }

    /**
     * Workflow function applied to subscriber entity before persistence.
     * Currently just returns entity without changes.
     */
    private CompletableFuture<Object> processSubscriber(Object entityObj) {
        // entityObj is ObjectNode
        ObjectNode entity = (ObjectNode) entityObj;
        // Example: could add timestamp if not present
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Workflow function applied to game entity before persistence.
     * Sends notification asynchronously to all subscribers about this game.
     * Can modify the game entity if needed.
     */
    private CompletableFuture<Object> processGame(Object entityObj) {
        ObjectNode gameEntity = (ObjectNode) entityObj;
        String date = gameEntity.has("date") ? gameEntity.get("date").asText() : "";
        String homeTeam = gameEntity.has("homeTeam") ? gameEntity.get("homeTeam").asText() : "";
        String awayTeam = gameEntity.has("awayTeam") ? gameEntity.get("awayTeam").asText() : "";
        int homeScore = gameEntity.has("homeScore") ? gameEntity.get("homeScore").asInt() : 0;
        int awayScore = gameEntity.has("awayScore") ? gameEntity.get("awayScore").asInt() : 0;

        // Fire and forget notification asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).thenAccept(subscribers -> {
                    if (subscribers.isEmpty()) {
                        logger.info("No subscribers found for notification about game {} vs {}", homeTeam, awayTeam);
                        return;
                    }
                    StringBuilder summary = new StringBuilder();
                    summary.append("NBA Game Notification:\n");
                    summary.append(String.format("%s vs %s on %s: %d - %d\n", homeTeam, awayTeam, date, homeScore, awayScore));

                    subscribers.forEach(subscriberNode -> {
                        JsonNode emailNode = subscriberNode.get("email");
                        if (emailNode != null && !emailNode.isNull()) {
                            String email = emailNode.asText();
                            // Simulate sending email
                            logger.info("Sending email to {}: \n{}", email, summary);
                        }
                    });
                }).join();
            } catch (Exception e) {
                logger.error("Error sending notifications for game {} vs {}: {}", homeTeam, awayTeam, e.getMessage());
            }
        });

        // Optionally modify the game entity, e.g. add notificationSent flag
        gameEntity.put("notificationSent", true);

        return CompletableFuture.completedFuture(gameEntity);
    }

    /* CONTROLLER ENDPOINTS */

    @PostMapping("/subscribe")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", request.getEmail()));

        return entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenCompose(arrayNode -> {
                    if (arrayNode.size() > 0) {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.ok(new SubscriptionResponse("Already subscribed", request.getEmail())));
                    }
                    ObjectNode subscriberNode = objectMapper.createObjectNode();
                    subscriberNode.put("email", request.getEmail());
                    subscriberNode.put("subscribedAt", Instant.now().toString());

                    return entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriberNode, this::processSubscriber)
                            .thenApply(id -> ResponseEntity.ok(new SubscriptionResponse("Subscription successful", request.getEmail())));
                });
    }

    @GetMapping("/subscribers")
    public CompletableFuture<ResponseEntity<List<String>>> getSubscribers() {
        logger.info("Retrieving all subscribers");
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<String> emails = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        JsonNode emailNode = node.get("email");
                        if (emailNode != null && !emailNode.isNull()) {
                            emails.add(emailNode.asText());
                        }
                    }
                    logger.info("Found {} subscribers", emails.size());
                    return ResponseEntity.ok(emails);
                });
    }

    @DeleteMapping("/subscribers/{email}")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> deleteSubscriber(@PathVariable @Email String email) {
        logger.info("Received request to delete subscriber: {}", email);

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", email));
        return entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenCompose(arrayNode -> {
                    if (arrayNode.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(new SubscriptionResponse("Subscriber not found", email)));
                    }
                    List<CompletableFuture<UUID>> deletes = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        JsonNode idNode = node.get("technicalId");
                        if (idNode != null && !idNode.isNull()) {
                            UUID id = UUID.fromString(idNode.asText());
                            deletes.add(entityService.deleteItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, id));
                        }
                    }
                    return CompletableFuture.allOf(deletes.toArray(new CompletableFuture[0]))
                            .thenApply(v -> ResponseEntity.ok(new SubscriptionResponse("Subscriber deleted", email)));
                });
    }

    @GetMapping("/games/all")
    public CompletableFuture<ResponseEntity<List<Game>>> getAllGames(
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) int limit,
            @RequestParam(name = "offset", defaultValue = "0") @Min(0) int offset) {
        logger.info("Retrieving all games with limit {} and offset {}", limit, offset);
        return entityService.getItems(ENTITY_NAME_GAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Game> games = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        Game game = convertNodeToGame(node);
                        if (game != null) {
                            games.add(game);
                        }
                    }
                    games.sort(Comparator.comparing(Game::getDate).thenComparing(Game::getGameId));
                    int fromIndex = Math.min(offset, games.size());
                    int toIndex = Math.min(offset + limit, games.size());
                    return ResponseEntity.ok(games.subList(fromIndex, toIndex));
                });
    }

    @GetMapping("/games/{date}")
    public CompletableFuture<ResponseEntity<List<Game>>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Retrieving games for date {}", date);

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));

        return entityService.getItemsByCondition(ENTITY_NAME_GAME, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    List<Game> games = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        Game game = convertNodeToGame(node);
                        if (game != null) {
                            games.add(game);
                        }
                    }
                    return ResponseEntity.ok(games);
                });
    }

    @DeleteMapping("/games/{date}")
    public CompletableFuture<ResponseEntity<FetchResponse>> deleteGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Received request to delete games for date: {}", date);

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));

        return entityService.getItemsByCondition(ENTITY_NAME_GAME, ENTITY_VERSION, condition)
                .thenCompose(arrayNode -> {
                    if (arrayNode.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(new FetchResponse("No games found for date", date, 0)));
                    }
                    List<CompletableFuture<UUID>> deletes = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        JsonNode idNode = node.get("technicalId");
                        if (idNode != null && !idNode.isNull()) {
                            UUID id = UUID.fromString(idNode.asText());
                            deletes.add(entityService.deleteItem(ENTITY_NAME_GAME, ENTITY_VERSION, id));
                        }
                    }
                    return CompletableFuture.allOf(deletes.toArray(new CompletableFuture[0]))
                            .thenApply(v -> ResponseEntity.ok(new FetchResponse("Games deleted for date", date, deletes.size())));
                });
    }

    /**
     * New fetch & store games controller.
     * Fetch external API, parse games, insert games one by one with workflow to send notifications.
     */
    @PostMapping("/games/fetch")
    public CompletableFuture<ResponseEntity<FetchResponse>> fetchAndNotify(@RequestBody @Valid FetchRequest request) {
        String dateParam = (request.getDate() != null) ? request.getDate() : LocalDate.now().toString();
        logger.info("Manual fetch and notify triggered for date {}", dateParam);

        String url = String.format(EXTERNAL_API_URL, dateParam);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String jsonResponse = restTemplate.getForObject(url, String.class);
                if (jsonResponse == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from external API");
                }
                return objectMapper.readTree(jsonResponse);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch or parse external API response: " + e.getMessage());
            }
        }).thenCompose(rootNode -> {
            if (!rootNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected JSON format from external API");
            }

            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (JsonNode node : rootNode) {
                ObjectNode gameNode = objectMapper.createObjectNode();
                gameNode.put("gameId", node.path("GameID").asText(""));
                gameNode.put("date", dateParam);
                gameNode.put("homeTeam", node.path("HomeTeam").asText(""));
                gameNode.put("awayTeam", node.path("AwayTeam").asText(""));
                gameNode.put("homeScore", node.path("HomeTeamScore").asInt(0));
                gameNode.put("awayScore", node.path("AwayTeamScore").asInt(0));
                // Add each game with workflow to send notification
                futures.add(entityService.addItem(ENTITY_NAME_GAME, ENTITY_VERSION, gameNode, this::processGame));
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> ResponseEntity.ok(new FetchResponse("Scores fetched and notifications sent", dateParam, futures.size())));
        });
    }

    private Game convertNodeToGame(JsonNode node) {
        try {
            return new Game(
                    node.path("gameId").asText(""),
                    node.path("date").asText(""),
                    node.path("homeTeam").asText(""),
                    node.path("awayTeam").asText(""),
                    node.path("homeScore").asInt(0),
                    node.path("awayScore").asInt(0)
            );
        } catch (Exception e) {
            logger.warn("Failed to convert node to Game: {}", e.getMessage());
            return null;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class SubscriptionResponse {
        private String message;
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @AllArgsConstructor
    public static class Game {
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
    }

    @Data
    @NoArgsConstructor
    public static class FetchRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    @AllArgsConstructor
    public static class FetchResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
```

---

### Summary of workflow logic moved:

- **Subscriber**: Added timestamp in workflow.

- **Game**:
  - Notifications to subscribers moved inside `processGame`.
  - `fetchAndNotify` controller fetches external data and calls `entityService.addItem` for each game with workflow `processGame`.
  - This avoids batch insert without workflow and moves side effects (notification) into workflow.
  - Added `notificationSent` flag in the entity inside workflow.

- Controller methods are simplified, only orchestrate high-level tasks.

---

This design ensures:

- Workflow functions handle async side effects related to the entity and related entities.
- Controllers remain clean.
- No recursion or infinite loops because workflows do not add/update/delete the same entity type.
- Notifications are sent asynchronously on each persisted game entity.

Let me know if you want me to adjust or further modularize!