Understood. The requirement is to move all asynchronous logic, including side effects such as sending notifications or supplementary entity writes, into the workflow functions `processSubscriber` and `processGame`. This will remove such logic from the controller endpoints and rely on the workflow functions to run asynchronously before the entity is persisted.

We must remember:

- The workflow function is invoked **before persisting** the entity.
- It receives the entity as an `ObjectNode` (Jackson JSON tree).
- We can modify the entity directly in place (e.g. `entity.put(...)`).
- We **cannot** add/update/delete entities of the same model inside the workflow (to avoid recursion).
- We **can** add/update/delete entities of **different** models.
- The workflow function supports async code and is a replacement for fire-and-forget tasks.
- The entityService can be used inside the workflow to add supplementary entities of different types.

---

### What async or side-effect logic can be moved?

1. **Subscriber workflow (`processSubscriber`)**:
    - Normalize email to lowercase (already done).
    - Currently, no async side effects, so nothing else to move.

2. **Game workflow (`processGame`)**:
    - The controller fetches games and adds them one by one.
    - The notification sending (`sendNotifications`) is fire and forget after games are stored.
    - According to the requirement, notification sending should be moved into the workflow function, so that notifications are sent immediately after each game is persisted.
    - Since adding/updating/deleting entities of the same model inside the workflow is forbidden, we **cannot** call `addItem` for `Game` inside `processGame`, but we can send notifications or add entities of other models if needed.
    - The notification logic uses subscribers list and sends emails.
    - Therefore, `processGame` should asynchronously send notifications.

3. **Fetch and Store Scores endpoint**:
    - The logic of fetching external API and parsing must remain in the controller (it’s the source of input).
    - But the storing + notification should be part of the workflow function.

4. **Subscribe endpoint**:
    - Just add subscriber, workflow normalizes email.

---

### How to implement?

- The controller endpoints become very thin: they only validate input and call `entityService.addItem` with the workflow function.
- The `processSubscriber` workflow normalizes email.
- The `processGame` workflow sends notifications asynchronously.
- The fetch endpoint fetches scores, then calls `addItem` on each game with the workflow function.
- The notification logic moves from controller to `processGame`.
- Since each game triggers notifications independently, to avoid spamming subscribers multiple times for many games, we can aggregate notifications per date if needed. But requirement does not forbid multiple notifications.
- Alternatively, we can add a separate entity for notifications or queue them. But that may be more complex.
- For now, keep it simple: each `processGame` sends notification about that game.

---

### Implementation notes

- Workflow functions receive `Object` but it will be `ObjectNode`.
- Use Jackson API to manipulate entity.
- Use `entityService` inside workflow to add supplementary entities of different types if needed.
- Use async code inside workflow (return `CompletableFuture`).
- Avoid calling `addItem` for the same entity model inside workflow.

---

### Updated code

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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyodaEntityPrototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_NAME_GAME = "Game";

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
    public static class SubscribersResponse {
        private List<String> subscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchScoresRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchScoresResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamesResponse {
        private int page;
        private int size;
        private long totalGames;
        private List<Game> games;
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
    public static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
    }

    /**
     * Workflow function for Subscriber entity.
     * Normalizes email to lowercase.
     */
    private CompletableFuture<Object> processSubscriber(Object entityData) {
        if (!(entityData instanceof ObjectNode)) {
            return CompletableFuture.completedFuture(entityData);
        }
        ObjectNode entity = (ObjectNode) entityData;

        // Normalize email to lowercase
        JsonNode emailNode = entity.get("email");
        if (emailNode != null && emailNode.isTextual()) {
            String email = emailNode.asText().toLowerCase(Locale.ROOT);
            entity.put("email", email);
        }

        // Add subscribedAt if not present
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }

        // No async side effects, so complete immediately
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Workflow function for Game entity.
     * Sends notification emails asynchronously after game is persisted.
     * Can add supplementary entities if needed.
     */
    private CompletableFuture<Object> processGame(Object entityData) {
        if (!(entityData instanceof ObjectNode)) {
            return CompletableFuture.completedFuture(entityData);
        }
        ObjectNode entity = (ObjectNode) entityData;

        // Fire-and-forget async notification sending
        CompletableFuture.runAsync(() -> {
            try {
                sendNotificationsForGame(entity);
            } catch (Exception e) {
                logger.error("Failed to send notifications in processGame workflow", e);
            }
        });

        // Return entity immediately for persistence
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Send notifications about new game to all subscribers.
     * This is invoked inside the Game workflow function asynchronously.
     */
    private void sendNotificationsForGame(ObjectNode gameEntity) throws Exception {
        // Build notification content for this game
        String date = safeText(gameEntity, "date");
        String homeTeam = safeText(gameEntity, "homeTeam");
        String awayTeam = safeText(gameEntity, "awayTeam");
        Integer homeScore = safeInt(gameEntity, "homeScore");
        Integer awayScore = safeInt(gameEntity, "awayScore");
        String status = safeText(gameEntity, "status");

        StringBuilder content = new StringBuilder();
        content.append("NBA Score Update for ").append(date).append(":\n");
        content.append(String.format("%s vs %s: %s-%s (%s)\n",
                homeTeam, awayTeam,
                homeScore != null ? homeScore : "N/A",
                awayScore != null ? awayScore : "N/A",
                status != null ? status : "Unknown"));

        // Retrieve subscribers
        CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subs = subsFuture.get(10, TimeUnit.SECONDS);

        if (subs != null) {
            for (JsonNode subNode : subs) {
                JsonNode emailNode = subNode.get("email");
                if (emailNode != null && emailNode.isTextual()) {
                    String email = emailNode.asText();

                    // Simulate sending email by logging
                    logger.info("Send email to {}: \n{}", email, content.toString());
                }
            }
        }
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) throws ExecutionException, InterruptedException {
        String email = request.getEmail().toLowerCase(Locale.ROOT);

        Condition condition = Condition.of("$.email", "EQUALS", email);
        SearchConditionRequest condRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> existingSubsFuture = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condRequest);
        ArrayNode existingSubs = existingSubsFuture.get();

        if (existingSubs != null && existingSubs.size() > 0) {
            logger.info("Subscription attempt for existing email: {}", email);
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", email));
        }

        Subscriber newSubscriber = new Subscriber(email, Instant.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, newSubscriber, this::processSubscriber);
        idFuture.get();

        logger.info("New subscriber added: {}", email);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubscribeResponse("Subscription successful", email));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> deleteSubscription(@Valid @RequestBody SubscribeRequest request) throws ExecutionException, InterruptedException {
        String email = request.getEmail().toLowerCase(Locale.ROOT);

        Condition condition = Condition.of("$.email", "EQUALS", email);
        SearchConditionRequest condRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> existingSubsFuture = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condRequest);
        ArrayNode existingSubs = existingSubsFuture.get();

        if (existingSubs == null || existingSubs.size() == 0) {
            logger.info("Delete subscription attempt for non-existing email: {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SubscribeResponse("Email not found in subscribers", email));
        }

        // Delete all matching subscribers (should be one)
        for (JsonNode subscriberNode : existingSubs) {
            UUID technicalId = UUID.fromString(subscriberNode.get("technicalId").asText());
            entityService.deleteItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, technicalId).get();
        }
        logger.info("Subscriber removed: {}", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription deleted", email));
    }

    @GetMapping("/subscribers")
    public SubscribersResponse getSubscribers() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subs = subsFuture.get();
        List<String> emails = new ArrayList<>();
        if (subs != null) {
            for (JsonNode node : subs) {
                JsonNode emailNode = node.get("email");
                if (emailNode != null && !emailNode.isNull()) {
                    emails.add(emailNode.asText());
                }
            }
        }
        logger.info("Retrieving all subscribers, count={}", emails.size());
        return new SubscribersResponse(emails);
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchScoresResponse> fetchAndStoreScores(@Valid @RequestBody FetchScoresRequest request) throws Exception {
        String dateStr = (request.getDate() != null) ? request.getDate() : java.time.LocalDate.now().toString();
        logger.info("Fetching NBA scores for date: {}", dateStr);

        List<Game> fetchedGames;
        try {
            fetchedGames = fetchScoresFromExternalApi(dateStr);
        } catch (Exception ex) {
            logger.error("Failed to fetch NBA scores from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch from external API");
        }

        // Add each game via entityService.addItem with workflow function
        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (Game game : fetchedGames) {
            futures.add(entityService.addItem(ENTITY_NAME_GAME, ENTITY_VERSION, game, this::processGame));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        logger.info("Stored {} games for date {}", fetchedGames.size(), dateStr);

        // No notification sending here — moved to processGame workflow

        return ResponseEntity.ok(new FetchScoresResponse("Scores fetched and stored", dateStr, fetchedGames.size()));
    }

    @GetMapping("/games/all")
    public GamesResponse getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) throws ExecutionException, InterruptedException {

        logger.info("Retrieving all games - page: {}, size: {}", page, size);
        CompletableFuture<ArrayNode> gamesFuture = entityService.getItems(ENTITY_NAME_GAME, ENTITY_VERSION);
        ArrayNode gamesArray = gamesFuture.get();

        List<Game> allGames = new ArrayList<>();
        if (gamesArray != null) {
            for (JsonNode node : gamesArray) {
                allGames.add(parseGameNode(node));
            }
        }

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allGames.size());
        List<Game> pageGames = fromIndex >= allGames.size() ? Collections.emptyList() : allGames.subList(fromIndex, toIndex);
        return new GamesResponse(page, size, allGames.size(), pageGames);
    }

    @GetMapping("/games/{date}")
    public GamesByDateResponse getGamesByDate(
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
            @PathVariable String date) throws ExecutionException, InterruptedException {
        logger.info("Retrieving games for date {}", date);

        Condition condition = Condition.of("$.date", "EQUALS", date);
        SearchConditionRequest condRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> filteredGamesFuture = entityService.getItemsByCondition(ENTITY_NAME_GAME, ENTITY_VERSION, condRequest);
        ArrayNode filteredGamesArray = filteredGamesFuture.get();

        List<Game> games = new ArrayList<>();
        if (filteredGamesArray != null) {
            for (JsonNode node : filteredGamesArray) {
                games.add(parseGameNode(node));
            }
        }
        return new GamesByDateResponse(date, games);
    }

    private List<Game> fetchScoresFromExternalApi(String date) throws Exception {
        String url = String.format(EXTERNAL_API_URL_TEMPLATE, date, API_KEY);
        logger.info("Calling external API: {}", url);
        String rawJson = restTemplate.getForObject(new URI(url), String.class);
        if (rawJson == null) throw new IllegalStateException("Empty response");
        JsonNode rootNode = objectMapper.readTree(rawJson);
        List<Game> gameList = new ArrayList<>();
        if (rootNode.isArray()) {
            for (JsonNode gameNode : rootNode) {
                gameList.add(parseGame(gameNode, date));
            }
        } else if (rootNode.isObject()) {
            gameList.add(parseGame(rootNode, date));
        }
        return gameList;
    }

    private Game parseGame(JsonNode node, String date) {
        return new Game(
                date,
                safeText(node, "HomeTeam"),
                safeText(node, "AwayTeam"),
                safeInt(node, "HomeTeamScore"),
                safeInt(node, "AwayTeamScore"),
                safeText(node, "Status")
        );
    }

    private Game parseGameNode(JsonNode node) {
        String date = safeText(node, "date");
        String homeTeam = safeText(node, "homeTeam");
        String awayTeam = safeText(node, "awayTeam");
        Integer homeScore = safeInt(node, "homeScore");
        Integer awayScore = safeInt(node, "awayScore");
        String status = safeText(node, "status");

        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, status);
    }

    private String safeText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private Integer safeInt(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && child.isInt()) return child.asInt();
        if (child != null && child.isTextual()) {
            try {
                return Integer.parseInt(child.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "INTERNAL_SERVER_ERROR");
        error.put("message", ex.getMessage());
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

### Summary of changes:

- **Moved all async notification sending to `processGame` workflow**, which asynchronously sends notifications after the game entity is ready to be persisted.
- **Controller no longer calls or triggers notifications explicitly.**
- **`processSubscriber` normalizes email and adds `subscribedAt` timestamp.**
- **Endpoints became simpler and only responsible for validation and calling `addItem` with the workflow function.**
- Used `ObjectNode` in workflow to manipulate entity fields directly as per requirement.

This approach is more robust, keeps business logic close to the data lifecycle, and frees controllers from side-effect logic.

Let me know if you want me to adjust further or add comments!