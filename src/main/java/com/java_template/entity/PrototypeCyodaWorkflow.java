Certainly! To comply with the new architecture requirement, we will move all asynchronous logic and related side effects into the workflow functions named `process{entity_name}`. These functions will be applied to the entity **before** persistence, and will be responsible for modifying the entity state or triggering asynchronous tasks (like sending notifications) without modifying the entity of the same model (to avoid recursion).

---

### What will be moved to workflow functions?

1. **In `fetchScores`:**
   - The current async storing of games will remain in the controller because it is the persistence point.
   - The **sending notifications async task** will be moved into the `processCyodaEntity` workflow function.
   - Any transformation or enrichment of the entity (e.g. adding extra fields) should also be done inside the workflow.

2. **For `subscribe`:**
   - No async side effect currently, so the workflow will just ensure any data normalization or enrichment if needed.

---

### Key points for workflow functions:

- They receive the entity **as ObjectNode** (JSON tree node).
- Can modify this ObjectNode directly to change state before storage.
- Can call entityService to add/get entities of *different* models.
- Cannot call add/update/delete on the *same* entityModel.
- Can execute async code, including fire-and-forget tasks.
- Since they return CompletableFuture<ObjectNode>, we can chain async work.

---

### Updated full code with all async work moved to workflow functions and controllers "freed":

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
     * Workflow function for CyodaEntity (Game) entities.
     * - Accepts an ObjectNode representing a single game entity.
     * - Modifies the entity if necessary.
     * - Retrieves subscribers and sends notifications asynchronously (fire-and-forget).
     * - Can add/get entities of other models, but NOT modify current entityModel.
     */
    private CompletableFuture<ObjectNode> processCyodaEntity(ObjectNode entity) {
        // You can modify the entity here before persistence.
        // Example: add a processed timestamp
        entity.put("processedAt", Instant.now().toString());

        // Fire-and-forget sending notifications asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Retrieve subscribers
                SearchConditionRequest condition = SearchConditionRequest.group("AND"); // no filter = all
                CompletableFuture<ArrayNode> subsFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition);
                ArrayNode subsArray = subsFuture.get();

                List<String> emails = new ArrayList<>();
                subsArray.forEach(subNode -> {
                    JsonNode emailNode = subNode.get("email");
                    if (emailNode != null && emailNode.isTextual()) {
                        emails.add(emailNode.asText());
                    }
                });

                // Compose notification message from this game entity
                String homeTeam = entity.path("homeTeam").asText("Unknown");
                String awayTeam = entity.path("awayTeam").asText("Unknown");
                String date = entity.path("date").asText("UnknownDate");
                String homeScore = entity.has("homeScore") && !entity.get("homeScore").isNull()
                        ? entity.get("homeScore").asText()
                        : "?";
                String awayScore = entity.has("awayScore") && !entity.get("awayScore").isNull()
                        ? entity.get("awayScore").asText()
                        : "?";

                String notification = String.format("NBA Score for %s: %s vs %s => %s - %s", date, homeTeam, awayTeam, homeScore, awayScore);

                for (String email : emails) {
                    // TODO: implement real email delivery here
                    logger.info("Sending notification to {}: {}", email, notification);
                }

                logger.info("Notifications sent for game on {}", date);
            } catch (Exception e) {
                logger.error("Error during notification sending in workflow", e);
            }
        });

        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Workflow function for Subscriber entities.
     * - Normalizes email to lowercase.
     * - Adds subscription timestamp if missing.
     */
    private CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        // Normalize email to lowercase
        if (entity.has("email") && entity.get("email").isTextual()) {
            String email = entity.get("email").asText().toLowerCase();
            entity.put("email", email);
        }
        // Add subscribedAt timestamp if missing
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }
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

        // Convert external data into list of ObjectNode entities for persistence
        List<ObjectNode> gameNodes = new ArrayList<>();
        if (apiResponse.isArray()) {
            apiResponse.forEach(node -> {
                ObjectNode gameNode = objectMapper.createObjectNode();
                gameNode.put("date", request.getDate());
                gameNode.put("homeTeam", node.path("HomeTeam").asText(null));
                gameNode.put("awayTeam", node.path("AwayTeam").asText(null));
                if (node.hasNonNull("HomeTeamScore")) {
                    gameNode.put("homeScore", node.path("HomeTeamScore").asInt());
                } else {
                    gameNode.putNull("homeScore");
                }
                if (node.hasNonNull("AwayTeamScore")) {
                    gameNode.put("awayScore", node.path("AwayTeamScore").asInt());
                } else {
                    gameNode.putNull("awayScore");
                }

                // Add additional info fields (optional)
                ObjectNode additional = objectMapper.createObjectNode();
                node.fieldNames().forEachRemaining(field -> {
                    if (!List.of("HomeTeam", "AwayTeam", "HomeTeamScore", "AwayTeamScore").contains(field)) {
                        additional.set(field, node.get(field));
                    }
                });
                gameNode.set("additionalInfo", additional);

                gameNodes.add(gameNode);
            });
        } else {
            logger.warn("External API response is not an array for date {}", request.getDate());
        }

        try {
            // Persist all games with workflow function processCyodaEntity
            CompletableFuture<List<UUID>> addFut = entityService.addItems(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    gameNodes,
                    this::processCyodaEntity
            );
            addFut.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error storing games in EntityService", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store games");
        }

        logger.info("Stored {} games for {}", gameNodes.size(), request.getDate());
        // NO explicit async notifications here anymore - moved to workflow
        return ResponseEntity.ok(new ApiResponse("success", "Scores fetched and stored successfully."));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse> subscribe(@RequestBody @Valid SubscribeRequest request) throws ExecutionException, InterruptedException {
        ObjectNode subNode = objectMapper.createObjectNode();
        subNode.put("email", request.getEmail());
        // subscribedAt will be added in workflow if missing

        CompletableFuture<UUID> addFut = entityService.addItem("Subscriber", ENTITY_VERSION, subNode, this::processSubscriber);
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
    public ResponseEntity<List<String>> getSubscribers() throws ExecutionException, InterruptedException {
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

    @GetMapping("/games/{date}")
    public ResponseEntity<List<ObjectNode>> getGamesByDate(
            @PathVariable
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be YYYY-MM-DD") String date) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date)
        );
        CompletableFuture<ArrayNode> gamesFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        ArrayNode gamesArray = gamesFuture.get();

        List<ObjectNode> games = new ArrayList<>();
        gamesArray.forEach(node -> {
            if (node.isObject()) {
                games.add((ObjectNode) node);
            }
        });
        return ResponseEntity.ok(games);
    }

    @GetMapping("/games/all")
    public ResponseEntity<List<ObjectNode>> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> gamesFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode gamesArray = gamesFuture.get();
        List<ObjectNode> allGames = new ArrayList<>();
        gamesArray.forEach(node -> {
            if (node.isObject()) {
                allGames.add((ObjectNode) node);
            }
        });
        int from = Math.min(page * size, allGames.size());
        int to = Math.min(from + size, allGames.size());
        return ResponseEntity.ok(allGames.subList(from, to));
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

### Summary of the changes:

- **All async tasks like sending notifications are moved to `processCyodaEntity` workflow function**, which runs **before** the entity is persisted.
- Workflow functions receive and modify entity as `ObjectNode`, so all entity modifications happen in the workflow.
- Controllers become thin wrappers calling `entityService.addItem/addItems` with the entity and the appropriate workflow function.
- `processSubscriber` normalizes email and adds subscription timestamp inside the workflow.
- No direct asynchronous side effects or complex logic remain in the controller methods.
- This design complies with the requirement:
  - Workflow functions run asynchronously on the entity before persistence.
  - Can modify entity state.
  - Can call entityService on other models.
  - Cannot modify the same entity model to avoid recursion.

---

If you want, I can further help with unit tests or specific enhancements for the workflow functions.