```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + EXTERNAL_API_KEY;

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // DTOs

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Subscriber {
        private String email;
        private LocalDate subscribedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Game {
        private LocalDate date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
    }

    @Data
    @AllArgsConstructor
    public static class FetchScoresResponse {
        private String date;
        private int gamesFetched;
        private int subscribersNotified;
    }

    // Workflow functions

    /**
     * Workflow function to process Subscriber entities before persistence.
     * This function receives the Subscriber entity, can modify it, and returns it.
     */
    private Subscriber processSubscriber(Subscriber subscriber) {
        // Example: normalize email to lowercase before saving
        if (subscriber.getEmail() != null) {
            subscriber.setEmail(subscriber.getEmail().toLowerCase(Locale.ROOT));
        }
        // Additional processing can be added here
        return subscriber;
    }

    /**
     * Workflow function to process Game entities before persistence.
     * This function receives the Game entity, can modify it, and returns it.
     */
    private Game processGame(Game game) {
        // Example: trim team names or additional logic before persistence
        if (game.getHomeTeam() != null) {
            game.setHomeTeam(game.getHomeTeam().trim());
        }
        if (game.getAwayTeam() != null) {
            game.setAwayTeam(game.getAwayTeam().trim());
        }
        return game;
    }

    // We'll store subscribers and games by date using EntityService instead of local maps.

    @PostMapping("/subscribe")
    public CompletableFuture<ResponseEntity<Void>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Subscribe request received for email: {}", request.getEmail());
        String email = request.getEmail().toLowerCase(Locale.ROOT);

        // Check if subscriber with this email already exists
        return entityService.getItemsByCondition(
                "Subscriber",
                ENTITY_VERSION,
                com.java_template.common.util.SearchConditionRequest.group("AND",
                        com.java_template.common.util.Condition.of("$.email", "EQUALS", email))
        ).thenCompose(arrayNode -> {
            if (arrayNode.size() > 0) {
                logger.error("Email {} is already subscribed", email);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already subscribed");
            }
            Subscriber newSubscriber = new Subscriber(email, LocalDate.now());
            // Use new addItem method with workflow function processSubscriber
            return entityService.addItem("Subscriber", ENTITY_VERSION, newSubscriber, this::processSubscriber)
                    .thenApply(id -> {
                        logger.info("Subscribed new email: {}", email);
                        return ResponseEntity.status(HttpStatus.CREATED).build();
                    });
        });
    }

    @GetMapping("/subscribers")
    public CompletableFuture<List<String>> getSubscribers() {
        logger.info("Fetching all subscriber emails");
        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<String> emails = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        JsonNode emailNode = node.get("email");
                        if (emailNode != null && !emailNode.isNull()) {
                            emails.add(emailNode.asText());
                        }
                    });
                    return emails;
                });
    }

    @PostMapping("/fetch-scores")
    public ResponseEntity<FetchScoresResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetch scores request for date: {}", request.getDate());
        LocalDate requestedDate;
        try {
            requestedDate = LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: {}", request.getDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        CompletableFuture.runAsync(() -> fetchAndNotify(requestedDate)); // async fire-and-forget
        return ResponseEntity.ok(new FetchScoresResponse(request.getDate(), -1, -1)); // subscriber count will be updated later asynchronously
    }

    @GetMapping("/games/all")
    public CompletableFuture<List<Game>> getAllGames() {
        logger.info("Fetching all games stored");
        return entityService.getItems("Game", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Game> games = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        try {
                            Game game = objectMapper.treeToValue(node, Game.class);
                            games.add(game);
                        } catch (Exception e) {
                            logger.warn("Failed to parse game entity: {}", e.getMessage());
                        }
                    });
                    return games;
                });
    }

    @GetMapping("/games/{date}")
    public CompletableFuture<List<Game>> getGamesByDate(@PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Fetching games for date: {}", date);
        LocalDate queryDate;
        try {
            queryDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format in path: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        return entityService.getItemsByCondition(
                "Game",
                ENTITY_VERSION,
                com.java_template.common.util.SearchConditionRequest.group("AND",
                        com.java_template.common.util.Condition.of("$.date", "EQUALS", queryDate.toString()))
        ).thenApply(arrayNode -> {
            List<Game> games = new ArrayList<>();
            arrayNode.forEach(node -> {
                try {
                    Game game = objectMapper.treeToValue(node, Game.class);
                    games.add(game);
                } catch (Exception e) {
                    logger.warn("Failed to parse game entity: {}", e.getMessage());
                }
            });
            return games;
        });
    }

    private void fetchAndNotify(LocalDate date) {
        logger.info("Starting fetch and notify for date: {}", date);
        try {
            String url = String.format(EXTERNAL_API_URL_TEMPLATE, date);
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                logger.error("Empty response from external API for date: {}", date);
                return;
            }
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (!root.isArray()) {
                logger.error("Unexpected JSON structure from external API for date: {}", date);
                return;
            }
            List<Game> fetchedGames = new ArrayList<>();
            for (JsonNode gameNode : root) {
                String homeTeam = safeGetText(gameNode, "HomeTeam");
                String awayTeam = safeGetText(gameNode, "AwayTeam");
                Integer homeScore = safeGetInt(gameNode, "HomeTeamScore");
                Integer awayScore = safeGetInt(gameNode, "AwayTeamScore");
                if (homeTeam == null || awayTeam == null) {
                    logger.warn("Skipping game with incomplete team info: {}", gameNode.toString());
                    continue;
                }
                fetchedGames.add(new Game(date, homeTeam, awayTeam, homeScore, awayScore));
            }
            // Delete existing games for this date
            entityService.getItemsByCondition(
                    "Game",
                    ENTITY_VERSION,
                    com.java_template.common.util.SearchConditionRequest.group("AND",
                            com.java_template.common.util.Condition.of("$.date", "EQUALS", date.toString()))
            ).thenCompose(existingGamesNodes -> {
                List<CompletableFuture<UUID>> deleteFutures = new ArrayList<>();
                existingGamesNodes.forEach(node -> {
                    JsonNode technicalIdNode = node.get("technicalId");
                    if (technicalIdNode != null && !technicalIdNode.isNull()) {
                        try {
                            UUID technicalId = UUID.fromString(technicalIdNode.asText());
                            deleteFutures.add(entityService.deleteItem("Game", ENTITY_VERSION, technicalId));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                });
                return CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0]));
            }).join();

            // Add new games using addItems with workflow function processGame applied to each game individually
            List<CompletableFuture<UUID>> addFutures = new ArrayList<>();
            for (Game game : fetchedGames) {
                addFutures.add(entityService.addItem("Game", ENTITY_VERSION, game, this::processGame));
            }
            CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0])).join();

            logger.info("Stored {} games for date {}", fetchedGames.size(), date);
            sendEmailNotifications(date, fetchedGames);
        } catch (Exception ex) {
            logger.error("Error during fetch and notify process for date {}: {}", date, ex.getMessage(), ex);
        }
    }

    private String safeGetText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Integer safeGetInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull() && f.isInt()) ? f.asInt() : null;
    }

    private void sendEmailNotifications(LocalDate date, List<Game> games) {
        logger.info("Preparing to send email notifications for date {}", date);
        entityService.getItems("Subscriber", ENTITY_VERSION).thenAccept(arrayNode -> {
            int subscriberCount = arrayNode.size();
            arrayNode.forEach(node -> {
                JsonNode emailNode = node.get("email");
                if (emailNode != null && !emailNode.isNull()) {
                    String email = emailNode.asText();
                    CompletableFuture.runAsync(() -> {
                        logger.info("Sending email to {} with {} games summary for {}", email, games.size(), date);
                        // TODO: Implement real email sending here
                    });
                }
            });
            logger.info("Sent notifications to {} subscribers for date {}", subscriberCount, date);
        }).join();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

### Explanation of changes:

- Added two workflow functions:

  - `processSubscriber(Subscriber subscriber)`: normalizes email to lowercase before persistence.

  - `processGame(Game game)`: trims team names before persistence.

- Updated `subscribe()` method to call the new `entityService.addItem` method with workflow function as last argument:

  ```java
  entityService.addItem("Subscriber", ENTITY_VERSION, newSubscriber, this::processSubscriber)
  ```

- Updated `fetchAndNotify()` method to add individual games with the workflow function:

  ```java
  entityService.addItem("Game", ENTITY_VERSION, game, this::processGame)
  ```

  because `addItems` bulk method does not support the workflow function individually per item as per given info.

- The workflow function signature is consistent with the new expected interface: a function taking entity data and returning the (possibly modified) entity.

---

If you need the workflow function to be asynchronous or return a `CompletableFuture`, please clarify. The example here is synchronous and returns the modified entity directly. Adjust accordingly if asynchronous processing is required.