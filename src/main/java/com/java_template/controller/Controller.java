package com.java_template.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
@EnableAsync
public class Controller {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;
    private static final String API_KEY = "test"; // TODO: secure key
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";
    private static final String ENTITY_NAME = "Game";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        @Email
        @NotBlank
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
    public static class FetchGamesRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchGamesResponse {
        private String message;
        private String date;
        private int gamesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String email;
        private OffsetDateTime subscribedAt;
    }

    /**
     * Async fire-and-forget notification sending to all subscribers about the new game.
     */
    @Async
    public void sendNotificationAsync(ObjectNode gameEntity) {
        if (subscribers.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder("New NBA Game stored:\n");
            sb.append(String.format("%s vs %s on %s\n",
                    gameEntity.path("homeTeam").asText("N/A"),
                    gameEntity.path("awayTeam").asText("N/A"),
                    gameEntity.path("date").asText("N/A")));
            subscribers.keySet().forEach(email -> log.info("Email to {}:\n{}", email, sb));
        } catch (Exception e) {
            log.error("Error sending notifications asynchronously", e);
        }
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        log.info("Subscription request for: {}", email);
        if (subscribers.containsKey(email)) {
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", email));
        }
        subscribers.put(email, new Subscriber(email, OffsetDateTime.now()));
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<FetchGamesResponse> fetchAndStoreGames(@RequestBody(required = false) @Valid FetchGamesRequest request) {
        String dateStr = Optional.ofNullable(request).map(FetchGamesRequest::getDate).filter(s -> !s.isBlank())
                .orElse(LocalDate.now().toString());
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }
        // Async fetch + store + notifications handled outside workflow
        CompletableFuture.runAsync(() -> fetchStore(date));
        int currentCount = 0; // No sync count available at this point
        return ResponseEntity.ok(new FetchGamesResponse("Async fetch/store started", dateStr, currentCount));
    }

    /**
     * Fetch NBA games from external API, convert each to ObjectNode entity,
     * then persist them via entityService without workflow function.
     */
    private void fetchStore(LocalDate date) {
        String dateStr = date.toString();
        String url = String.format(NBA_API_URL_TEMPLATE, dateStr, API_KEY);
        try {
            URI uri = new URI(url);
            String resp = restTemplate.getForObject(uri, String.class);
            if (resp == null) return;
            JsonNode root = objectMapper.readTree(resp);
            List<ObjectNode> entities = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(node -> {
                    ObjectNode gameNode = parseGameToObjectNode(node, dateStr);
                    if (gameNode != null) entities.add(gameNode);
                });
            } else {
                ObjectNode gameNode = parseGameToObjectNode(root, dateStr);
                if (gameNode != null) entities.add(gameNode);
            }
            if (!entities.isEmpty()) {
                entityService.addItems(ENTITY_NAME, ENTITY_VERSION, entities); // Workflow argument removed
            }
        } catch (URISyntaxException e) {
            log.error("URI error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Fetch/store error", e);
        }
    }

    private ObjectNode parseGameToObjectNode(JsonNode node, String dateStr) {
        if (node == null || !node.isObject()) return null;
        ObjectNode gameNode = objectMapper.createObjectNode();
        gameNode.put("date", dateStr);
        gameNode.put("homeTeam", node.path("HomeTeam").asText(null));
        gameNode.put("awayTeam", node.path("AwayTeam").asText(null));
        if (!node.path("HomeTeamScore").isNull()) gameNode.put("homeScore", node.path("HomeTeamScore").asInt());
        else gameNode.putNull("homeScore");
        if (!node.path("AwayTeamScore").isNull()) gameNode.put("awayScore", node.path("AwayTeamScore").asInt());
        else gameNode.putNull("awayScore");

        ObjectNode otherFields = objectMapper.createObjectNode();
        node.fieldNames().forEachRemaining(f -> {
            if (!List.of("HomeTeam", "AwayTeam", "HomeTeamScore", "AwayTeamScore").contains(f)) {
                otherFields.set(f, node.get(f));
            }
        });
        gameNode.put("otherDetails", otherFields.toString());
        return gameNode;
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<String>> getSubscribers() {
        return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
    }

    @GetMapping("/games/all")
    public CompletableFuture<ResponseEntity<List<ObjectNode>>> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<ObjectNode> all = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        if (node.isObject()) all.add((ObjectNode) node);
                    });
                    int from = Math.min(page * size, all.size());
                    int to = Math.min(from + size, all.size());
                    return ResponseEntity.ok(all.subList(from, to));
                });
    }

    @GetMapping("/games/{date}")
    public CompletableFuture<ResponseEntity<List<ObjectNode>>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD") String date) {
        Condition condition = Condition.of("$.date", "EQUALS", date);
        SearchConditionRequest condReq = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condReq)
                .thenApply(arrayNode -> {
                    List<ObjectNode> list = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        if (node.isObject()) list.add((ObjectNode) node);
                    });
                    return ResponseEntity.ok(list);
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException ex) {
        Map<String, String> err = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, String> err = Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        );
        return new ResponseEntity<>(err, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}