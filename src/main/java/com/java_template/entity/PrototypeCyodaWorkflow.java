Certainly! Moving asynchronous logic and entity state mutation into the workflow function is the right approach to cleanly separate concerns and keep controllers thin. We will:

- Move the logic that fetches external scores, parses them, stores supplementary entities, and notifies subscribers into the `processGame` workflow function.
- Modify the entity (which will be an `ObjectNode`) inside this function if needed.
- The controller will just call `entityService.addItems(...)` with the workflow function and return immediately.
- We will remove async tasks from controller methods and move all async/fetch/notify logic into the workflow function.

---

### Key considerations before the refactor:

- The workflow function receives a single entity object (one game at a time, wrapped as `ObjectNode`).
- We **cannot** add/update/delete the same entity model inside the workflow (to avoid recursion).
- We **can** add/get entities of **different** entity models.
- Notification sending and external API fetching is async and should be done inside the workflow.
- The workflow function returns a `CompletableFuture<Object>` with the (possibly modified) entity.

---

### Implementation plan:

- Rename `processGame` to accept `Object` and cast to `ObjectNode` for mutation.
- Fetch the external API only once for the batch? Unfortunately the workflow is called per entity; so we cannot fetch all scores in one call here.
- But the current controller calls `addItems` with a list of games fetched externally, so the external fetch is done outside the workflow.
- According to your instructions, async tasks like fetch and notify should be moved **inside** the workflow.
- However, since workflow is called per entity (per game), fetching all games inside each workflow would be inefficient and recursive.
- So maybe we should:
  - Change the controller to accept a list of game data (or partial data) to persist.
  - The workflow function enriches/fetches supplementary data, sends notifications, etc.
- Alternatively, if the controller triggers the fetch (once per date), it can add entities with an empty list or trigger a dummy entity that triggers the workflow to fetch and add secondary entities.
- Since the current API design expects a fetchScores endpoint that triggers the fetch from an external API and stores data, we can implement a **special entity** "FetchRequest" with workflow `processFetchRequest` to do the fetch, persist games, and notify.
- The `Game` workflow function then handles only individual game mutations if needed.

---

### For this example, I will:

- Introduce a new entity model `"FetchRequest"` with workflow `processFetchRequest` that:
  - Fetches external scores for given date.
  - Adds new "Game" entities via `entityService.addItems("Game", ...)` **without workflow** (since recursion is forbidden).
  - Sends notifications.
- The controller POST `/scores/fetch` will add a `FetchRequest` entity with the date.
- The workflow `processFetchRequest` will do the heavy lifting asynchronously.
- The `Game` workflow `processGame` will remain simple (could mutate entities if needed).
- This cleanly separates concerns, keeps controller minimal, and moves async logic to workflows.

---

### Updated full Java code with these changes:

```java
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
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String NBA_API_KEY = "test"; // TODO: Replace with config/env variable
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;
    private final Map<String, LocalDate> subscribers = new HashMap<>();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Game {
        private String date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Invalid date format. Use YYYY-MM-DD.")
        private String date;
    }

    // --- Workflow function for Game entities ---
    // Called once per Game entity before persistence
    private final Function<Object, CompletableFuture<Object>> processGame = entity -> {
        // entity is an ObjectNode representing a Game
        ObjectNode gameNode = (ObjectNode) entity;

        // Example mutation: add a "processedAt" timestamp
        gameNode.put("processedAt", System.currentTimeMillis());

        // Could add additional enrichments, validations, etc.

        return CompletableFuture.completedFuture(gameNode);
    };

    // --- Workflow function for FetchRequest entities ---
    // This function triggers the fetch, store, and notify asynchronously BEFORE persisting FetchRequest entity
    private final Function<Object, CompletableFuture<Object>> processFetchRequest = entity -> {
        ObjectNode fetchRequestNode = (ObjectNode) entity;

        String dateStr = fetchRequestNode.path("date").asText(null);
        if (dateStr == null) {
            logger.warn("FetchRequest entity missing 'date' field");
            return CompletableFuture.completedFuture(fetchRequestNode);
        }

        logger.info("Starting async fetch and store for date {}", dateStr);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Fetch external NBA scores JSON array
                String url = String.format(NBA_API_URL_TEMPLATE, dateStr, NBA_API_KEY);
                URI uri = new URI(url);
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri).GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.error("Failed to fetch NBA scores for {}. Status code: {}", dateStr, response.statusCode());
                    return fetchRequestNode;
                }

                JsonNode root = objectMapper.readTree(response.body());
                if (!root.isArray()) {
                    logger.error("Unexpected response format from NBA API for {}", dateStr);
                    return fetchRequestNode;
                }

                // 2. Parse and build list of Game entities (as ObjectNode)
                List<ObjectNode> gamesToStore = new ArrayList<>();
                for (JsonNode gameNode : root) {
                    ObjectNode g = parseGameNode(gameNode);
                    if (g != null) {
                        // Add date field if missing (some APIs have different date fields)
                        if (!g.has("date")) {
                            g.put("date", dateStr);
                        }
                        gamesToStore.add(g);
                    }
                }
                logger.info("Parsed {} games for date {}", gamesToStore.size(), dateStr);

                // 3. Store games using entityService.addItems WITHOUT workflow (cannot call processGame here to avoid recursion)
                // This is allowed since these are different entities than FetchRequest
                CompletableFuture<List<UUID>> addGamesFuture = entityService.addItems("Game", ENTITY_VERSION, gamesToStore, null);
                addGamesFuture.join();
                logger.info("Stored {} games for date {}", gamesToStore.size(), dateStr);

                // 4. Notify subscribers (fire-and-forget)
                notifySubscribers(dateStr, gamesToStore);

            } catch (Exception e) {
                logger.error("Exception in processFetchRequest workflow: {}", e.getMessage(), e);
            }
            return fetchRequestNode; // Return original entity unmodified
        });
    };

    private void notifySubscribers(String dateStr, List<ObjectNode> games) {
        if (subscribers.isEmpty()) {
            logger.info("No subscribers to notify for date {}", dateStr);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("NBA Scores for ").append(dateStr).append(":\n\n");
        for (ObjectNode g : games) {
            sb.append(String.format("%s vs %s: %d - %d (%s)\n",
                    g.path("awayTeam").asText("N/A"),
                    g.path("homeTeam").asText("N/A"),
                    g.hasNonNull("awayScore") ? g.get("awayScore").asInt() : 0,
                    g.hasNonNull("homeScore") ? g.get("homeScore").asInt() : 0,
                    g.path("status").asText("Unknown")
            ));
        }

        // Dummy notification: just log emails and messages
        subscribers.keySet().forEach(email -> logger.info("Notify {}:\n{}", email, sb.toString()));

        logger.info("Notifications sent to {} subscribers", subscribers.size());
    }

    private ObjectNode parseGameNode(JsonNode n) {
        try {
            ObjectNode game = objectMapper.createObjectNode();

            // Normalize date field
            String date = Optional.ofNullable(n.path("Day").asText(null))
                    .or(() -> Optional.ofNullable(n.path("DateTime").asText(null)).filter(s -> s.length() >= 10).map(s -> s.substring(0, 10)))
                    .orElse(null);
            if (date != null) {
                game.put("date", date);
            }

            // Required fields
            game.put("homeTeam", n.path("HomeTeam").asText(""));
            game.put("awayTeam", n.path("AwayTeam").asText(""));

            // Optional scores
            if (n.hasNonNull("HomeTeamScore")) {
                game.put("homeScore", n.get("HomeTeamScore").asInt());
            }
            if (n.hasNonNull("AwayTeamScore")) {
                game.put("awayScore", n.get("AwayTeamScore").asInt());
            }

            game.put("status", n.path("Status").asText("Unknown"));

            return game;
        } catch (Exception e) {
            logger.warn("Failed to parse game node: {}", e.getMessage());
            return null;
        }
    }

    // --- Controller endpoints ---

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Subscribe requested for email {}", request.getEmail());
        subscribers.put(request.getEmail().toLowerCase(Locale.ROOT), LocalDate.now());
        return ResponseEntity.ok(new MessageResponse("Subscription successful"));
    }

    @DeleteMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> unsubscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Unsubscribe requested for email {}", request.getEmail());
        if (subscribers.remove(request.getEmail().toLowerCase(Locale.ROOT)) != null) {
            return ResponseEntity.ok(new MessageResponse("Unsubscription successful"));
        } else {
            return ResponseEntity.ok(new MessageResponse("Email not found in subscription list"));
        }
    }

    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getSubscribers() {
        return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
    }

    @GetMapping(path = "/games/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(name = "page", required = false, defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", required = false, defaultValue = "100") @Min(1) int size) {

        SearchConditionRequest sc = SearchConditionRequest.group("AND"); // no conditions
        List<JsonNode> nodes = entityService.getItemsByCondition("Game", ENTITY_VERSION, sc)
                .thenApply(arrayNode -> {
                    List<JsonNode> list = new ArrayList<>();
                    arrayNode.forEach(list::add);
                    return list;
                }).join();

        List<Game> games = new ArrayList<>();
        for (JsonNode node : nodes) {
            Game g = convertNodeToGame(node);
            if (g != null) games.add(g);
        }

        int from = page * size;
        if (from >= games.size()) return ResponseEntity.ok(Collections.emptyList());
        int to = Math.min(from + size, games.size());
        return ResponseEntity.ok(games.subList(from, to));
    }

    @GetMapping(path = "/games/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getGamesByDate(@PathVariable("date") @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dateStr) {
        try {
            LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        Condition cond = Condition.of("$.date", "EQUALS", dateStr);
        SearchConditionRequest sc = SearchConditionRequest.group("AND", cond);
        List<JsonNode> nodes = entityService.getItemsByCondition("Game", ENTITY_VERSION, sc)
                .thenApply(arrayNode -> {
                    List<JsonNode> list = new ArrayList<>();
                    arrayNode.forEach(list::add);
                    return list;
                }).join();

        List<Game> games = new ArrayList<>();
        for (JsonNode node : nodes) {
            Game g = convertNodeToGame(node);
            if (g != null) games.add(g);
        }

        return ResponseEntity.ok(games);
    }

    private Game convertNodeToGame(JsonNode node) {
        try {
            String date = node.path("date").asText(null);
            String home = node.path("homeTeam").asText(null);
            String away = node.path("awayTeam").asText(null);

            if (date == null || home == null || away == null) return null;

            Integer hs = node.hasNonNull("homeScore") ? node.get("homeScore").asInt() : null;
            Integer as = node.hasNonNull("awayScore") ? node.get("awayScore").asInt() : null;
            String status = node.path("status").asText("Unknown");

            return new Game(date, home, away, hs, as, status);
        } catch (Exception e) {
            logger.warn("Failed to convert node to Game: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Now the /scores/fetch endpoint just creates a FetchRequest entity with the date,
     * and the heavy lifting is done asynchronously inside processFetchRequest workflow.
     */
    @PostMapping(path = "/scores/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        // Validate date
        try {
            LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        // Build FetchRequest entity
        ObjectNode fetchRequestEntity = objectMapper.createObjectNode();
        fetchRequestEntity.put("date", request.getDate());

        // Add FetchRequest entity with workflow processFetchRequest which will do async fetch/store/notify
        CompletableFuture<UUID> future = entityService.addItem("FetchRequest", ENTITY_VERSION, fetchRequestEntity, processFetchRequest);
        UUID id = future.join();

        logger.info("FetchRequest entity created with id {}", id);

        return ResponseEntity.ok(new MessageResponse("Scores fetching started"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }
}
```

---

### Summary of changes:

- Introduced a new entity model `"FetchRequest"` with a workflow `processFetchRequest`.
- `/scores/fetch` endpoint now just creates a `FetchRequest` entity with the requested date.
- The `processFetchRequest` workflow asynchronously:
  - Fetches NBA scores from external API.
  - Parses and creates `Game` entities.
  - Persists these games via `entityService.addItems("Game", ...)` **without workflow** (to avoid recursion).
  - Sends notifications to subscribers.
- The `Game` workflow `processGame` is slim and can mutate or enrich individual game entities before persistence.
- Removed all async logic from controllers.
- Controllers are now minimal, just creating entities that trigger workflows.

---

This approach fully leverages the new workflow feature, making async tasks part of the entity lifecycle and keeping controllers clean. It follows all your requirements and recommendations.

Let me know if you want me to implement workflows for other entities or further improvements!