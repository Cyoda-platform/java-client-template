```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
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

    private static final String API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + API_KEY;

    private static final String ENTITY_NAME_SUBSCRIBER = "subscriber";
    private static final String ENTITY_NAME_GAME = "game";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function applied to subscriber entity before persistence.
     * Here you can modify the subscriber entity asynchronously if needed.
     */
    private CompletableFuture<Object> processSubscriber(Object entity) {
        // Example: just return the entity as is asynchronously
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Workflow function applied to game entity before persistence.
     * Here you can modify the game entity asynchronously if needed.
     */
    private CompletableFuture<Object> processGame(Object entity) {
        // Example: just return the entity as is asynchronously
        return CompletableFuture.completedFuture(entity);
    }

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
                    Subscriber subscriber = new Subscriber(request.getEmail(), Instant.now());
                    // Pass workflow function processSubscriber as argument
                    return entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber, this::processSubscriber)
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
                    // There could be multiple, delete all found
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

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchResponse> fetchAndNotify(@RequestBody @Valid FetchRequest request) {
        String dateParam = (request.getDate() != null) ? request.getDate() : LocalDate.now().toString();
        logger.info("Manual fetch and notify triggered for date {}", dateParam);
        fetchStoreAndNotify(dateParam);
        return ResponseEntity.ok(new FetchResponse("Scores fetching and notification started", dateParam, -1));
    }

    @Async
    public void fetchStoreAndNotify(String date) {
        logger.info("Starting fetchStoreAndNotify for date {}", date);
        String url = String.format(EXTERNAL_API_URL, date);
        JsonNode rootNode;
        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                logger.error("Empty response from external API for date {}", date);
                return;
            }
            rootNode = entityService.getObjectMapper().readTree(jsonResponse); // using entityService's ObjectMapper if available
        } catch (Exception e) {
            logger.error("Failed to fetch or parse external API response for date {}: {}", date, e.getMessage());
            return;
        }
        if (!rootNode.isArray()) {
            logger.error("Unexpected JSON format: expected array but got {}", rootNode.getNodeType());
            return;
        }
        List<Game> gamesList = new ArrayList<>();
        for (JsonNode node : rootNode) {
            try {
                Game game = new Game(
                        node.path("GameID").asText(""),
                        node.path("Day").asText(date),
                        node.path("HomeTeam").asText(""),
                        node.path("AwayTeam").asText(""),
                        node.path("HomeTeamScore").asInt(0),
                        node.path("AwayTeamScore").asInt(0)
                );
                gamesList.add(game);
            } catch (Exception e) {
                logger.warn("Skipping a game due to parse error: {}", e.getMessage());
            }
        }
        // Save all games with workflow processGame
        entityService.addItems(ENTITY_NAME_GAME, ENTITY_VERSION, gamesList).thenCompose(savedIds -> {
            // Since addItems does not accept workflow, but addItem does, if you want to apply
            // workflow on each game, you need to add individually with workflow.
            // But here we keep addItems for batch save.
            // Alternatively, if bulk save with workflow is required, addItem individually with workflow.
            return CompletableFuture.completedFuture(savedIds);
        }).join();

        logger.info("Stored {} games for date {}", gamesList.size(), date);
        sendNotifications(date, gamesList);
    }

    private void sendNotifications(String date, List<Game> gamesList) {
        // Retrieve subscribers
        entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).thenAccept(arrayNode -> {
            logger.info("Sending notifications to {} subscribers for date {}", arrayNode.size(), date);
            if (arrayNode.isEmpty()) {
                logger.info("No subscribers found, skipping notifications");
                return;
            }
            StringBuilder summary = new StringBuilder();
            summary.append("NBA Scores for ").append(date).append(":\n");
            for (Game g : gamesList) {
                summary.append(String.format("%s vs %s : %d - %d\n",
                        g.getHomeTeam(), g.getAwayTeam(), g.getHomeScore(), g.getAwayScore()));
            }
            for (JsonNode subscriberNode : arrayNode) {
                JsonNode emailNode = subscriberNode.get("email");
                if (emailNode != null && !emailNode.isNull()) {
                    String email = emailNode.asText();
                    logger.info("Sending email to {}:\n{}", email, summary);
                }
            }
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

### Explanation of changes:

- Added two new private methods for workflows:

  ```java
  private CompletableFuture<Object> processSubscriber(Object entity) { ... }
  private CompletableFuture<Object> processGame(Object entity) { ... }
  ```

- These methods match the expected signature: take entity as input and return `CompletableFuture<Object>` with possibly modified entity.

- Updated all calls to `entityService.addItem(...)` to pass the workflow function as the last argument:

  ```java
  entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber, this::processSubscriber)
  ```

- In `subscribe(...)`, replaced `.thenApply(...)` with `.thenCompose(...)` to properly chain async calls and added workflow.

- In `fetchStoreAndNotify(...)`, note that `addItems` is used for batch adding games. If workflow per item is required, you would have to add items individually with workflow (not shown here as it wasn't originally called like this). The example keeps original batch saving for efficiency.

- The workflow methods currently just return the entity asynchronously without modification but can be extended to modify the entity or perform other async operations before persistence.

---

If you want me to update other places to use workflow with `addItem` or add individual saving with workflow for games, just ask.