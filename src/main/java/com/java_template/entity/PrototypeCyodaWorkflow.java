package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure config or env variable
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

    private CompletableFuture<ObjectNode> processSubscriber(ObjectNode subscriberEntity) {
        JsonNode emailNode = subscriberEntity.get("email");
        if (emailNode != null && !emailNode.isNull()) {
            subscriberEntity.put("email", emailNode.asText().toLowerCase(Locale.ROOT));
        }
        if (subscriberEntity.get("subscribedAt") == null) {
            subscriberEntity.put("subscribedAt", LocalDate.now().toString());
        }
        return CompletableFuture.completedFuture(subscriberEntity);
    }

    private CompletableFuture<ObjectNode> processGame(ObjectNode gameEntity) {
        JsonNode homeTeamNode = gameEntity.get("homeTeam");
        if (homeTeamNode != null && !homeTeamNode.isNull()) {
            gameEntity.put("homeTeam", homeTeamNode.asText().trim());
        }
        JsonNode awayTeamNode = gameEntity.get("awayTeam");
        if (awayTeamNode != null && !awayTeamNode.isNull()) {
            gameEntity.put("awayTeam", awayTeamNode.asText().trim());
        }
        return CompletableFuture.completedFuture(gameEntity);
    }

    private CompletableFuture<ObjectNode> processFetchScores(ObjectNode fetchScoresEntity) {
        return CompletableFuture.supplyAsync(() -> {
            String dateStr = fetchScoresEntity.hasNonNull("date") ? fetchScoresEntity.get("date").asText() : null;
            if (dateStr == null) {
                throw new RuntimeException("FetchScores entity missing 'date' field");
            }
            LocalDate date;
            try {
                date = LocalDate.parse(dateStr);
            } catch (DateTimeParseException e) {
                throw new RuntimeException("Invalid date format in FetchScores entity: " + dateStr);
            }

            logger.info("Workflow processFetchScores started for date: {}", date);

            try {
                // 1) Fetch external API data
                String url = String.format(EXTERNAL_API_URL_TEMPLATE, dateStr);
                String jsonResponse = restTemplate.getForObject(url, String.class);
                if (jsonResponse == null) {
                    throw new RuntimeException("Empty response from external API for date: " + dateStr);
                }
                JsonNode root = objectMapper.readTree(jsonResponse);
                if (!root.isArray()) {
                    throw new RuntimeException("Unexpected JSON structure from external API for date: " + dateStr);
                }

                List<ObjectNode> gamesToAdd = new ArrayList<>();
                for (JsonNode gameNode : root) {
                    String homeTeam = safeGetText(gameNode, "HomeTeam");
                    String awayTeam = safeGetText(gameNode, "AwayTeam");
                    Integer homeScore = safeGetInt(gameNode, "HomeTeamScore");
                    Integer awayScore = safeGetInt(gameNode, "AwayTeamScore");
                    if (homeTeam == null || awayTeam == null) {
                        logger.warn("Skipping game with incomplete team info: {}", gameNode.toString());
                        continue;
                    }
                    ObjectNode gameEntity = objectMapper.createObjectNode();
                    gameEntity.put("date", dateStr);
                    gameEntity.put("homeTeam", homeTeam);
                    gameEntity.put("awayTeam", awayTeam);
                    if (homeScore != null) gameEntity.put("homeScore", homeScore);
                    if (awayScore != null) gameEntity.put("awayScore", awayScore);
                    gamesToAdd.add(gameEntity);
                }

                logger.info("Fetched {} games from external API for date {}", gamesToAdd.size(), date);

                // 2) Delete existing games for this date
                List<JsonNode> existingGames = entityService.getItemsByCondition(
                        "Game",
                        ENTITY_VERSION,
                        com.java_template.common.util.SearchConditionRequest.group("AND",
                                com.java_template.common.util.Condition.of("$.date", "EQUALS", dateStr))
                ).join();

                List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
                for (JsonNode existingGameNode : existingGames) {
                    JsonNode technicalIdNode = existingGameNode.get("technicalId");
                    if (technicalIdNode != null && !technicalIdNode.isNull()) {
                        try {
                            UUID technicalId = UUID.fromString(technicalIdNode.asText());
                            deleteFutures.add(entityService.deleteItem("Game", ENTITY_VERSION, technicalId).thenAccept(uuid -> {}));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).join();
                logger.info("Deleted {} existing games for date {}", deleteFutures.size(), date);

                // 3) Add new games with workflow
                List<CompletableFuture<UUID>> addFutures = gamesToAdd.stream()
                        .map(gameEntity -> entityService.addItem("Game", ENTITY_VERSION, gameEntity, this::processGame))
                        .collect(Collectors.toList());
                CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0])).join();
                logger.info("Added {} new games for date {}", gamesToAdd.size(), date);

                // 4) Send notifications asynchronously
                List<JsonNode> subscribers = entityService.getItems("Subscriber", ENTITY_VERSION).join();
                int subscriberCount = subscribers.size();

                for (JsonNode subscriberNode : subscribers) {
                    JsonNode emailNode = subscriberNode.get("email");
                    if (emailNode != null && !emailNode.isNull()) {
                        String email = emailNode.asText();
                        CompletableFuture.runAsync(() -> {
                            logger.info("Sending email to {} with {} games summary for {}", email, gamesToAdd.size(), date);
                            // TODO: Implement real email sending here
                        });
                    }
                }
                logger.info("Sent notifications to {} subscribers for date {}", subscriberCount, date);

                fetchScoresEntity.put("gamesFetched", gamesToAdd.size());
                fetchScoresEntity.put("subscribersNotified", subscriberCount);

            } catch (Exception e) {
                logger.error("Error in processFetchScores workflow for date {}: {}", date, e.getMessage(), e);
                fetchScoresEntity.put("error", e.getMessage());
            }
            return fetchScoresEntity;
        });
    }

    private String safeGetText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Integer safeGetInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull() && f.isInt()) ? f.asInt() : null;
    }

    @PostMapping("/subscribe")
    public CompletableFuture<ResponseEntity<Void>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Subscribe request received for email: {}", request.getEmail());
        String email = request.getEmail().toLowerCase(Locale.ROOT);

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
            ObjectNode newSubscriber = objectMapper.createObjectNode();
            newSubscriber.put("email", email);
            newSubscriber.put("subscribedAt", LocalDate.now().toString());
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
    public CompletableFuture<ResponseEntity<FetchScoresResponse>> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetch scores request for date: {}", request.getDate());

        LocalDate date;
        try {
            date = LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: {}", request.getDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        ObjectNode fetchScoresEntity = objectMapper.createObjectNode();
        fetchScoresEntity.put("date", date.toString());

        return entityService.addItem("FetchScores", ENTITY_VERSION, fetchScoresEntity, this::processFetchScores)
                .thenApply(id -> ResponseEntity.accepted().body(new FetchScoresResponse(date.toString(), -1, -1)));
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