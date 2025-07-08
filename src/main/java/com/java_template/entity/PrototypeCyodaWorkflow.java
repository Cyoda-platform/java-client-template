```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("cyoda-entity-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    private static final String ENTITY_NAME = "CyodaEntity";
    private static final String EXTERNAL_API_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        @Email
        @NotBlank
        private String email;
        private Instant subscribedAt;
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
        private Map<String, Object> additionalInfo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @Email
        @NotBlank
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        @NotNull
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ApiResponse {
        private String status;
        private String message;
    }

    /**
     * Workflow function that processes a Game entity asynchronously before persistence.
     * You can modify the entity state here or add/get entities of different models.
     * Must NOT add/update/delete entities of the same model to avoid infinite recursion.
     * This example simply returns the entity without changes.
     */
    private CompletableFuture<Game> processCyodaEntity(Game entity) {
        // Example: You can modify entity, add logs, or perform other async logic here.
        logger.debug("Processing Game entity in workflow: {}", entity);
        // Return the entity unchanged wrapped in CompletableFuture
        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<ApiResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetching scores for date: {}", request.getDate());
        String url = String.format(EXTERNAL_API_TEMPLATE, request.getDate());
        JsonNode apiResponse;
        try {
            apiResponse = objectMapper.readTree(restTemplate.getForObject(new URI(url), String.class));
        } catch (Exception e) {
            logger.error("Error fetching or parsing external data", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve external NBA data");
        }
        List<Game> games = new ArrayList<>();
        if (apiResponse.isArray()) {
            apiResponse.forEach(node -> {
                try {
                    Game g = new Game();
                    g.setDate(request.getDate());
                    g.setHomeTeam(node.path("HomeTeam").asText(null));
                    g.setAwayTeam(node.path("AwayTeam").asText(null));
                    g.setHomeScore(node.path("HomeTeamScore").isInt() ? node.path("HomeTeamScore").asInt() : null);
                    g.setAwayScore(node.path("AwayTeamScore").isInt() ? node.path("AwayTeamScore").asInt() : null);
                    Map<String, Object> additional = new HashMap<>();
                    node.fieldNames().forEachRemaining(field -> {
                        if (!List.of("HomeTeam", "AwayTeam", "HomeTeamScore", "AwayTeamScore").contains(field)) {
                            additional.put(field, node.get(field));
                        }
                    });
                    g.setAdditionalInfo(additional);
                    games.add(g);
                } catch (Exception ex) {
                    logger.warn("Skipping malformed game entry", ex);
                }
            });
        } else {
            logger.warn("External response is not array for date {}", request.getDate());
        }

        // Store games in entityService using new addItems with workflow function
        try {
            CompletableFuture<List<UUID>> addFut = entityService.addItems(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    games,
                    this::processCyodaEntity // Pass workflow function reference
            );
            addFut.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error storing games in EntityService", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store games");
        }

        logger.info("Stored {} games for {}", games.size(), request.getDate());
        sendNotificationsAsync(request.getDate(), games);
        return ResponseEntity.ok(new ApiResponse("success", "Scores fetched, stored, and notifications sent."));
    }

    @Async
    void sendNotificationsAsync(String date, List<Game> games) {
        CompletableFuture.runAsync(() -> {
            try {
                // Retrieve subscribers from entityService
                SearchConditionRequest condition = SearchConditionRequest.group("AND"); // no filter = all subscribers
                CompletableFuture<ArrayNode> subsFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition);
                ArrayNode subsArray = subsFuture.get();
                List<Subscriber> subscribers = new ArrayList<>();
                subsArray.forEach(node -> {
                    try {
                        Subscriber s = objectMapper.treeToValue(node, Subscriber.class);
                        subscribers.add(s);
                    } catch (Exception ex) {
                        logger.warn("Skipping malformed subscriber entry", ex);
                    }
                });

                logger.info("Sending emails for date {} to {} subscribers", date, subscribers.size());
                StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
                games.forEach(g -> summary.append(String.format("%s vs %s: %s - %s\n",
                        g.getHomeTeam(), g.getAwayTeam(),
                        g.getHomeScore() != null ? g.getHomeScore() : "?",
                        g.getAwayScore() != null ? g.getAwayScore() : "?")));
                for (Subscriber sub : subscribers) {
                    // TODO replace with actual email logic
                    logger.info("Email to {}: \n{}", sub.getEmail(), summary);
                }
                logger.info("Emails sent");
            } catch (Exception e) {
                logger.error("Error sending notifications", e);
            }
        });
    }

    /**
     * Workflow function for Subscriber entity.
     * This example returns the entity unchanged asynchronously.
     */
    private CompletableFuture<Subscriber> processSubscriber(Subscriber entity) {
        logger.debug("Processing Subscriber entity in workflow: {}", entity);
        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse> subscribe(@RequestBody @Valid SubscribeRequest request) throws ExecutionException, InterruptedException {
        Subscriber sub = new Subscriber(request.getEmail().toLowerCase(), Instant.now());
        CompletableFuture<UUID> addFut = entityService.addItem("Subscriber", ENTITY_VERSION, sub, this::processSubscriber);
        addFut.get();
        logger.info("Subscribed {}", request.getEmail());
        return ResponseEntity.ok(new ApiResponse("success", "Email subscribed successfully."));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<ApiResponse> deleteSubscription(@RequestParam @Email @NotBlank String email) throws ExecutionException, InterruptedException {
        String emailLower = email.toLowerCase();
        // Find subscriber by email
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", emailLower)
        );
        CompletableFuture<ArrayNode> subsFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition);
        ArrayNode subsArray = subsFuture.get();
        if (subsArray.isEmpty()) {
            logger.info("Attempt to unsubscribe non-existing email {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("error", "Email not found in subscribers."));
        }
        // Delete all matching subscribers (should be 1)
        List<CompletableFuture<UUID>> deleteFutures = new ArrayList<>();
        subsArray.forEach(node -> {
            UUID techId = UUID.fromString(node.get("technicalId").asText());
            deleteFutures.add(entityService.deleteItem("Subscriber", ENTITY_VERSION, techId));
        });
        for (CompletableFuture<UUID> f : deleteFutures) {
            f.get();
        }
        logger.info("Unsubscribed {}", email);
        return ResponseEntity.ok(new ApiResponse("success", "Email unsubscribed successfully."));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<Collection<String>> getSubscribers() throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND"); // no filter = all
        CompletableFuture<ArrayNode> subsFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition);
        ArrayNode subsArray = subsFuture.get();
        List<String> emails = new ArrayList<>();
        subsArray.forEach(node -> {
            JsonNode emailNode = node.get("email");
            if (emailNode != null && emailNode.isTextual()) {
                emails.add(emailNode.asText());
            }
        });
        return ResponseEntity.ok(emails);
    }

    @GetMapping("/games/all")
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> gamesFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode gamesArray = gamesFuture.get();
        List<Game> allGames = new ArrayList<>();
        gamesArray.forEach(node -> {
            try {
                Game g = objectMapper.treeToValue(node, Game.class);
                allGames.add(g);
            } catch (Exception ex) {
                logger.warn("Skipping malformed game entry", ex);
            }
        });
        int from = Math.min(page * size, allGames.size());
        int to = Math.min(from + size, allGames.size());
        return ResponseEntity.ok(allGames.subList(from, to));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<List<Game>> getGamesByDate(
            @PathVariable
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be YYYY-MM-DD") String date) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date)
        );
        CompletableFuture<ArrayNode> gamesFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        ArrayNode gamesArray = gamesFuture.get();
        List<Game> games = new ArrayList<>();
        gamesArray.forEach(node -> {
            try {
                Game g = objectMapper.treeToValue(node, Game.class);
                games.add(g);
            } catch (Exception ex) {
                logger.warn("Skipping malformed game entry", ex);
            }
        });
        return ResponseEntity.ok(games);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        logger.error("Error {} {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }
}
```

---

### Explanation of changes:

- Added a private workflow method `processCyodaEntity(Game entity)` that matches the new required signature and returns `CompletableFuture<Game>`.
- Passed this workflow method as the last argument to `entityService.addItems()` in `fetchScores` method.
- Added a similar `processSubscriber(Subscriber entity)` workflow method and passed it to `entityService.addItem()` in the `subscribe` method.
- These workflow functions currently just return the entity unchanged asynchronously, but you can modify them to change the entity state or perform async operations before persistence.
- This matches your new API for `entityService.addItem` and `addItems` that require a workflow function argument.

If you have other entity models that call `addItem` or `addItems`, similarly add and pass their workflow functions.