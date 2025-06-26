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

    private static final String NBA_API_KEY = "test"; // TODO: replace with config/env variable
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;
    private final Map<String, LocalDate> subscribers = Collections.synchronizedMap(new HashMap<>());

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

    // Workflow function processGame removed from this version
    // Workflow function processFetchRequest removed from this version

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        synchronized (subscribers) {
            subscribers.put(email, LocalDate.now());
        }
        logger.info("Subscribed email {}", email);
        return ResponseEntity.ok(new MessageResponse("Subscription successful"));
    }

    @DeleteMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> unsubscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        boolean removed;
        synchronized (subscribers) {
            removed = subscribers.remove(email) != null;
        }
        if (removed) {
            logger.info("Unsubscribed email {}", email);
            return ResponseEntity.ok(new MessageResponse("Unsubscription successful"));
        } else {
            logger.info("Email {} not found for unsubscription", email);
            return ResponseEntity.ok(new MessageResponse("Email not found in subscription list"));
        }
    }

    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getSubscribers() {
        synchronized (subscribers) {
            return ResponseEntity.ok(new ArrayList<>(subscribers.keySet()));
        }
    }

    @GetMapping(path = "/games/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Game>> getAllGames(
            @RequestParam(name = "page", required = false, defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", required = false, defaultValue = "100") @Min(1) int size) {
        SearchConditionRequest sc = SearchConditionRequest.group("AND");
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

    // The /scores/fetch endpoint now just creates a FetchRequest entity with the date, WITHOUT workflow.
    @PostMapping(path = "/scores/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        try {
            LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        ObjectNode fetchRequestEntity = objectMapper.createObjectNode();
        fetchRequestEntity.put("date", request.getDate());

        CompletableFuture<UUID> future = entityService.addItem("FetchRequest", ENTITY_VERSION, fetchRequestEntity);
        UUID id = future.join();

        logger.info("Created FetchRequest entity with id {}", id);

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