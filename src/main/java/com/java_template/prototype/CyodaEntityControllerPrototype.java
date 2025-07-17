package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda/prototype")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_KEY = "test"; // TODO: replace with secure key storage
    private static final String NBA_API_ENDPOINT_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    // Local cache for minor/utility entities only (subscribers, gamesByDate)
    private final Map<String, Instant> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<JsonNode>> gamesByDate = new ConcurrentHashMap<>();

    @Data
    public static class SubscriptionRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    // Assume "Subscription" is minor entity - keep local cache usage

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscriptionRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT).trim();
        if (subscribers.containsKey(email)) {
            logger.info("Subscription attempt for existing email: {}", email);
            return ResponseEntity.ok(Map.of("message", "Already subscribed", "email", email));
        }
        subscribers.put(email, Instant.now());
        logger.info("New subscriber added: {}", email);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Subscription successful", "email", email));
    }

    @PostMapping(path = "/fetch-scores", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        String dateStr = request.getDate();
        logger.info("Received fetch request for date: {}", dateStr);
        CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(String.format(NBA_API_ENDPOINT_TEMPLATE, dateStr, API_KEY));
                JsonNode responseJson = restTemplate.getForObject(uri, JsonNode.class);
                if (responseJson == null || !responseJson.isArray()) {
                    logger.error("Invalid response from external API for date {}: {}", dateStr, responseJson);
                    return;
                }
                List<JsonNode> games = new ArrayList<>();
                responseJson.forEach(games::add);
                gamesByDate.put(dateStr, games);
                logger.info("Stored {} games for date {}", games.size(), dateStr);
                sendEmailNotifications(dateStr, games);
            } catch (Exception ex) {
                logger.error("Error during fetchScores async for date " + dateStr, ex);
            }
        });
        return ResponseEntity.accepted().body(Map.of("message", "Fetch triggered", "date", dateStr));
    }

    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<String>>> getSubscribers() {
        List<String> emails = new ArrayList<>(subscribers.keySet());
        emails.sort(String::compareToIgnoreCase);
        logger.info("Retrieving {} subscribers", emails.size());
        return ResponseEntity.ok(Map.of("subscribers", emails));
    }

    @GetMapping(path = "/games/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<JsonNode>>> getAllGames() {
        logger.info("Retrieving all games across {} dates", gamesByDate.size());
        return ResponseEntity.ok(gamesByDate);
    }

    @GetMapping(path = "/games/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD") String date) {
        logger.info("Retrieving games for date: {}", date);
        List<JsonNode> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(Map.of("date", date, "games", games));
    }

    private void sendEmailNotifications(String date, List<JsonNode> games) {
        logger.info("Preparing notifications for {} subscribers on {}", subscribers.size(), date);
        if (subscribers.isEmpty()) {
            logger.info("No subscribers to notify");
            return;
        }
        StringBuilder summary = new StringBuilder("NBA Scores for ").append(date).append(":\n");
        games.forEach(game -> {
            String home = game.path("HomeTeam").asText("N/A");
            String away = game.path("AwayTeam").asText("N/A");
            String homeScore = game.path("HomeScore").asText("?");
            String awayScore = game.path("AwayScore").asText("?");
            summary.append(String.format("%s vs %s: %s - %s%n", away, home, awayScore, homeScore));
        });
        CompletableFuture.runAsync(() -> {
            subscribers.keySet().forEach(email -> {
                // TODO: implement real email send
                logger.info("Mock sending to {}: \n{}", email, summary);
            });
        });
    }

    // Now refactor main business entities with significant logic to use entityService
    // For example, suppose we have entities: Player, Team, and Match with business logic.
    // Since original code does not show those, below is an example template for one entity.

    // Example: Player entity controller methods using entityService

    private static final String PLAYER_ENTITY_NAME = "Player";

    @Data
    public static class Player {
        @JsonIgnore
        private UUID technicalId;

        @NotBlank
        private String name;
        private int age;
        private String position;
        // other fields, validations, etc.
    }

    @PostMapping(path = "/players", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> addPlayer(@RequestBody @Valid Player player) {
        return entityService.addItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, player);
    }

    @GetMapping(path = "/players/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Player> getPlayer(@PathVariable UUID id) {
        return entityService.getItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> objectMapper.convertValue(objectNode, Player.class));
    }

    @GetMapping(path = "/players", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<List<Player>> getAllPlayers() {
        return entityService.getItems(PLAYER_ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Player> players = new ArrayList<>();
                    arrayNode.forEach(node -> players.add(objectMapper.convertValue(node, Player.class)));
                    return players;
                });
    }

    @PutMapping(path = "/players/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> updatePlayer(@PathVariable UUID id, @RequestBody @Valid Player player) {
        return entityService.updateItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, id, player);
    }

    @DeleteMapping(path = "/players/{id}")
    public CompletableFuture<UUID> deletePlayer(@PathVariable UUID id) {
        return entityService.deleteItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, id);
    }

    // Similarly, for another main entity: Team

    private static final String TEAM_ENTITY_NAME = "Team";

    @Data
    public static class Team {
        @JsonIgnore
        private UUID technicalId;

        @NotBlank
        private String name;
        private String city;
        private String coach;
        // other fields, validations, etc.
    }

    @PostMapping(path = "/teams", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> addTeam(@RequestBody @Valid Team team) {
        return entityService.addItem(TEAM_ENTITY_NAME, ENTITY_VERSION, team);
    }

    @GetMapping(path = "/teams/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Team> getTeam(@PathVariable UUID id) {
        return entityService.getItem(TEAM_ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> objectMapper.convertValue(objectNode, Team.class));
    }

    @GetMapping(path = "/teams", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<List<Team>> getAllTeams() {
        return entityService.getItems(TEAM_ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Team> teams = new ArrayList<>();
                    arrayNode.forEach(node -> teams.add(objectMapper.convertValue(node, Team.class)));
                    return teams;
                });
    }

    @PutMapping(path = "/teams/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> updateTeam(@PathVariable UUID id, @RequestBody @Valid Team team) {
        return entityService.updateItem(TEAM_ENTITY_NAME, ENTITY_VERSION, id, team);
    }

    @DeleteMapping(path = "/teams/{id}")
    public CompletableFuture<UUID> deleteTeam(@PathVariable UUID id) {
        return entityService.deleteItem(TEAM_ENTITY_NAME, ENTITY_VERSION, id);
    }

    // Another main entity: Match

    private static final String MATCH_ENTITY_NAME = "Match";

    @Data
    public static class Match {
        @JsonIgnore
        private UUID technicalId;

        @NotBlank
        private String homeTeamId;
        @NotBlank
        private String awayTeamId;
        private String date;  // ISO Date as String or LocalDate with custom serialization
        private Integer homeScore;
        private Integer awayScore;
        // other fields, validations, etc.
    }

    @PostMapping(path = "/matches", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> addMatch(@RequestBody @Valid Match match) {
        return entityService.addItem(MATCH_ENTITY_NAME, ENTITY_VERSION, match);
    }

    @GetMapping(path = "/matches/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Match> getMatch(@PathVariable UUID id) {
        return entityService.getItem(MATCH_ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> objectMapper.convertValue(objectNode, Match.class));
    }

    @GetMapping(path = "/matches", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<List<Match>> getAllMatches() {
        return entityService.getItems(MATCH_ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Match> matches = new ArrayList<>();
                    arrayNode.forEach(node -> matches.add(objectMapper.convertValue(node, Match.class)));
                    return matches;
                });
    }

    @PutMapping(path = "/matches/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> updateMatch(@PathVariable UUID id, @RequestBody @Valid Match match) {
        return entityService.updateItem(MATCH_ENTITY_NAME, ENTITY_VERSION, id, match);
    }

    @DeleteMapping(path = "/matches/{id}")
    public CompletableFuture<UUID> deleteMatch(@PathVariable UUID id) {
        return entityService.deleteItem(MATCH_ENTITY_NAME, ENTITY_VERSION, id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, reason={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }
}