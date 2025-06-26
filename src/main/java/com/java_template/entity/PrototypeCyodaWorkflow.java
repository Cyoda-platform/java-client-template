```java
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
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, LocalDate> subscribers = new ConcurrentHashMap<>();

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
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Invalid date format. Use YYYY-MM-DD.")
        private String date;
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

    // Workflow function for Game entity, applied asynchronously before persistence
    private Function<Object, CompletableFuture<Object>> processGame = entity -> {
        // This function receives the entity data (Game), can modify state asynchronously if needed
        // Here, just return the entity as is wrapped in a completed future
        return CompletableFuture.completedFuture(entity);
    };

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received subscription for email: {}", request.getEmail());
        subscribers.putIfAbsent(request.getEmail().toLowerCase(Locale.ROOT), LocalDate.now());
        logger.info("Subscription successful for email: {}", request.getEmail());
        return ResponseEntity.ok(new MessageResponse("Subscription successful"));
    }

    @DeleteMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> unsubscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received unsubscribe request for email: {}", request.getEmail());
        boolean removed = subscribers.remove(request.getEmail().toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            logger.info("Unsubscription successful for email: {}", request.getEmail());
            return ResponseEntity.ok(new MessageResponse("Unsubscription successful"));
        } else {
            logger.info("Email not found for unsubscription: {}", request.getEmail());
            return ResponseEntity.ok(new MessageResponse("Email not found in subscription list"));
        }
    }

    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getSubscribers() {
        logger.info("Retrieving subscribers");
        return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
    }

    @GetMapping(path = "/games/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(name = "page", required = false, defaultValue = "0") @Min(value = 0, message = "Page index must be >= 0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "100") @Min(value = 1, message = "Size must be >= 1") int size) {
        logger.info("Fetching all games page={} size={}", page, size);

        // Use entityService to get all game entities via getItemsByCondition with no condition (empty group)
        SearchConditionRequest emptyCondition = SearchConditionRequest.group("AND"); // no conditions

        CompletableFuture<List<JsonNode>> futureEntities = entityService.getItemsByCondition("Game", ENTITY_VERSION, emptyCondition)
                .thenApply(arrayNode -> {
                    List<JsonNode> list = new ArrayList<>();
                    arrayNode.forEach(list::add);
                    return list;
                });

        List<Game> allGames = futureEntities.join().stream().map(this::toGame).filter(Objects::nonNull).toList();

        int from = page * size;
        if (from >= allGames.size()) return ResponseEntity.ok(Collections.emptyList());
        int to = Math.min(from + size, allGames.size());
        return ResponseEntity.ok(allGames.subList(from, to));
    }

    @GetMapping(path = "/games/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getGamesByDate(
            @PathVariable("date") @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Invalid date format. Use YYYY-MM-DD.") String dateStr) {
        logger.info("Fetching games for date: {}", dateStr);
        try {
            LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM-DD.");
        }

        Condition condition = Condition.of("$.date", "EQUALS", dateStr);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        CompletableFuture<List<JsonNode>> futureEntities = entityService.getItemsByCondition("Game", ENTITY_VERSION, searchCondition)
                .thenApply(arrayNode -> {
                    List<JsonNode> list = new ArrayList<>();
                    arrayNode.forEach(list::add);
                    return list;
                });

        List<Game> games = futureEntities.join().stream().map(this::toGame).filter(Objects::nonNull).toList();
        return ResponseEntity.ok(games);
    }

    @PostMapping(path = "/scores/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Starting fetch for date: {}", request.getDate());
        LocalDate date = LocalDate.parse(request.getDate());
        CompletableFuture.runAsync(() -> {
            try {
                fetchStoreAndNotify(date);
            } catch (Exception e) {
                logger.error("Async error: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(new MessageResponse("Scores fetching started"));
    }

    private void fetchStoreAndNotify(LocalDate date) throws URISyntaxException {
        String dateStr = date.toString();
        String url = String.format(NBA_API_URL_TEMPLATE, dateStr, NBA_API_KEY);
        logger.info("Calling external API: {}", url);
        URI uri = new URI(url);
        String raw;
        try {
            raw = new java.net.http.HttpClient.Builder().build().send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            logger.error("Fetch failed: {}", e.getMessage());
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) {
                logger.warn("Expected array, got {}", root.getNodeType());
                return;
            }
            List<Game> list = new ArrayList<>();
            for (JsonNode node : root) {
                Game g = parseGame(node);
                if (g != null) list.add(g);
            }
            // Store games via entityService.addItems with workflow function processGame
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems("Game", ENTITY_VERSION, list, processGame);
            idsFuture.join(); // wait for completion
            logger.info("Stored {} games for {}", list.size(), dateStr);
            notifySubscribers(dateStr, list);
        } catch (Exception ex) {
            logger.error("Parse error: {}", ex.getMessage());
        }
    }

    private Game toGame(JsonNode node) {
        try {
            String date = Optional.ofNullable(node.path("date").asText(null)).orElse(null);
            String home = node.path("homeTeam").asText(null);
            String away = node.path("awayTeam").asText(null);
            Integer hs = node.has("homeScore") && !node.get("homeScore").isNull() ? node.get("homeScore").asInt() : null;
            Integer as = node.has("awayScore") && !node.get("awayScore").isNull() ? node.get("awayScore").asInt() : null;
            String status = node.path("status").asText("Unknown");
            if (date == null || home == null || away == null) {
                logger.warn("Incomplete game entity: {}", node);
                return null;
            }
            return new Game(date, home, away, hs, as, status);
        } catch (Exception e) {
            logger.warn("Conversion exception: {}", e.getMessage());
            return null;
        }
    }

    private Game parseGame(JsonNode n) {
        try {
            String date = Optional.ofNullable(n.path("Day").asText(null))
                    .or(() -> Optional.ofNullable(n.path("DateTime").asText(null)).filter(s -> s.length() >= 10).map(s -> s.substring(0, 10)))
                    .orElse(null);
            String home = n.path("HomeTeam").asText(null);
            String away = n.path("AwayTeam").asText(null);
            Integer hs = n.has("HomeTeamScore") && !n.get("HomeTeamScore").isNull() ? n.get("HomeTeamScore").asInt() : null;
            Integer as = n.has("AwayTeamScore") && !n.get("AwayTeamScore").isNull() ? n.get("AwayTeamScore").asInt() : null;
            String status = n.path("Status").asText("Unknown");
            if (date == null || home == null || away == null) {
                logger.warn("Incomplete game: {}", n);
                return null;
            }
            return new Game(date, home, away, hs, as, status);
        } catch (Exception e) {
            logger.warn("Parse exception: {}", e.getMessage());
            return null;
        }
    }

    private void notifySubscribers(String dateStr, List<Game> games) {
        if (subscribers.isEmpty()) {
            logger.info("No subscribers for {}", dateStr);
            return;
        }
        StringBuilder sb = new StringBuilder("NBA Scores for ").append(dateStr).append(":\n\n");
        games.forEach(g -> sb.append(String.format("%s vs %s: %d - %d (%s)\n",
                g.getAwayTeam(), g.getHomeTeam(),
                Optional.ofNullable(g.getAwayScore()).orElse(0),
                Optional.ofNullable(g.getHomeScore()).orElse(0),
                g.getStatus())));
        subscribers.keySet().forEach(email -> logger.info("Sending to {}:\n{}", email, sb.toString())); // TODO: integrate real email service
        logger.info("Notifications sent to {} subscribers", subscribers.size());
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

### Explanation of changes:

- **Added a workflow function `processGame`**:
  - Signature: `Function<Object, CompletableFuture<Object>>`
  - It takes the entity object (in this case, a `Game` object).
  - Returns the same object asynchronously via `CompletableFuture.completedFuture(entity)`.
  - You can extend this function to modify the entity or perform additional asynchronous actions before persistence.

- **Updated the call of `entityService.addItems`** in `fetchStoreAndNotify`:
  - Added the `processGame` workflow function as the last parameter.
  - Signature now: `entityService.addItems(String entityModel, int entityVersion, List<?> entities, Function<Object, CompletableFuture<Object>> workflow)`

- **No other logic changes** were made; all existing functionality remains unchanged except the integration of the new workflow argument.

This follows the requirements:

> `entityService.addItem` (and similarly `addItems`) now expects the workflow function as an argument, named `workflow=process{entity_name}`, here `processGame`.

Let me know if you want me to update other usages of `addItem` / `addItems` similarly or implement an actual entity workflow logic!