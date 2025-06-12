package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, String> subscriptionConfirmations = new ConcurrentHashMap<>();
    private final Map<String, String> unsubscriptionConfirmations = new ConcurrentHashMap<>();
    private final Map<String, List<Game>> gamesByDate = new ConcurrentHashMap<>();
    private SchedulerConfig schedulerConfig = new SchedulerConfig("0 0 18 * * ?", true);

    private static final String ENTITY_NAME = "Subscriber";
    private static final String GAME_ENTITY_NAME = "Game";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow for Subscriber entity
    private CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        String email = entity.get("email").asText();
        logger.info("Workflow: Sending subscription confirmed email to {}", email);
        CompletableFuture.runAsync(() -> {
            // TODO: Implement real email sending logic here
            logger.info("[Email] Subscription confirmed email sent to {}", email);
        });
        return CompletableFuture.completedFuture(entity);
    }

    // Workflow for Game entity
    private CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        logger.info("Workflow: Sending notifications to all subscribers about new game scores");
        CompletableFuture.runAsync(() -> {
            subscribers.values().forEach(sub -> {
                logger.info("[Email] Sending daily NBA scores email to {}", sub.getEmail());
                // TODO: Send actual email with game info
            });
        });
        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received subscription request for email '{}'", request.getEmail());
        if (subscribers.containsKey(request.getEmail())) {
            logger.error("Email '{}' already subscribed", request.getEmail());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }
        String token = UUID.randomUUID().toString();
        subscriptionConfirmations.put(token, request.getEmail());
        CompletableFuture.runAsync(() -> {
            logger.info("Sending subscription confirmation email to {} with token {}", request.getEmail(), token);
            // TODO: integrate real email sending here (confirmation token)
        });
        return ResponseEntity.ok(Map.of("message", "Subscription request received. Please confirm via email."));
    }

    @PostMapping("/subscribe/confirm")
    public ResponseEntity<Map<String, String>> confirmSubscription(@RequestBody @Valid ConfirmTokenRequest request) throws Exception {
        logger.info("Confirm subscription with token {}", request.getToken());
        String email = subscriptionConfirmations.remove(request.getToken());
        if (email == null) {
            logger.error("Invalid or expired subscription confirmation token: {}", request.getToken());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        Subscriber subscriber = new Subscriber(email, Instant.now());
        subscribers.put(email, subscriber);

        ObjectNode subscriberNode = objectMapper.valueToTree(subscriber);

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                subscriberNode,
                this::processSubscriber
        );
        idFuture.join();

        logger.info("Subscription confirmed for email {}", email);
        return ResponseEntity.ok(Map.of("message", "Subscription confirmed."));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, String>> unsubscribe(@RequestBody @Valid UnsubscribeRequest request) {
        logger.info("Received unsubscribe request for email '{}'", request.getEmail());
        if (!subscribers.containsKey(request.getEmail())) {
            logger.error("Email '{}' not subscribed", request.getEmail());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not subscribed");
        }
        String token = UUID.randomUUID().toString();
        unsubscriptionConfirmations.put(token, request.getEmail());
        CompletableFuture.runAsync(() -> {
            logger.info("Sending unsubscribe confirmation email to {} with token {}", request.getEmail(), token);
            // TODO: integrate real email sending here (confirmation token)
        });
        return ResponseEntity.ok(Map.of("message", "Unsubscribe request received. Please confirm via email."));
    }

    @PostMapping("/unsubscribe/confirm")
    public ResponseEntity<Map<String, String>> confirmUnsubscription(@RequestBody @Valid ConfirmTokenRequest request) throws Exception {
        logger.info("Confirm unsubscription with token {}", request.getToken());
        String email = unsubscriptionConfirmations.remove(request.getToken());
        if (email == null) {
            logger.error("Invalid or expired unsubscription confirmation token: {}", request.getToken());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        subscribers.remove(email);

        String condition = String.format("email='%s'", email.replace("'", "\\'"));
        ArrayNode filtered = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition).join();
        for (JsonNode node : filtered) {
            UUID technicalId = UUID.fromString(node.get("technicalId").asText());
            entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, technicalId).join();
        }
        logger.info("Unsubscription confirmed for email {}", email);

        CompletableFuture.runAsync(() -> {
            logger.info("Sending unsubscription confirmed email to {}", email);
            // TODO: integrate real email sending here (unsubscribed confirmation)
        });

        return ResponseEntity.ok(Map.of("message", "Unsubscription confirmed."));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersPageResponse> getSubscribers(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive int size) throws Exception {
        logger.info("Retrieving subscribers page {} size {}", page, size);
        ArrayNode allEntities = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).join();
        List<String> allEmails = new ArrayList<>();
        for (JsonNode node : allEntities) {
            String email = node.get("email").asText();
            allEmails.add(email);
            Instant subscribedAt = Instant.parse(node.get("subscribedAt").asText());
            subscribers.put(email, new Subscriber(email, subscribedAt));
        }
        int total = allEmails.size();
        int fromIndex = page * size;
        if (fromIndex >= total) {
            return ResponseEntity.ok(new SubscribersPageResponse(page, size, (total + size - 1) / size, total, List.of()));
        }
        int toIndex = Math.min(fromIndex + size, total);
        List<String> pageContent = allEmails.subList(fromIndex, toIndex);
        return ResponseEntity.ok(new SubscribersPageResponse(page, size, (total + size - 1) / size, total, pageContent));
    }

    @PostMapping("/games/fetch")
    public ResponseEntity<Map<String, String>> fetchAndStoreScores(@RequestBody @Valid FetchScoresRequest request) {
        logger.info("Fetching NBA scores for date {}", request.getDate());
        CompletableFuture.runAsync(() -> {
            try {
                String apiKey = "test"; // TODO: secure config
                String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + request.getDate() + "?key=" + apiKey;
                URI uri = new URI(url);
                String rawJson = restTemplate.getForObject(uri, String.class);
                if (rawJson == null) {
                    logger.error("Empty response from NBA API for date {}", request.getDate());
                    return;
                }
                JsonNode rootNode = objectMapper.readTree(rawJson);
                if (!rootNode.isArray()) {
                    logger.warn("NBA API response is not an array for date {}: {}", request.getDate(), rawJson);
                    return;
                }
                ArrayNode root = (ArrayNode) rootNode;
                List<Game> games = new ArrayList<>();
                for (JsonNode node : root) {
                    ObjectNode gameNode = objectMapper.createObjectNode();
                    gameNode.put("gameId", node.path("GameID").asText());
                    gameNode.put("date", request.getDate());
                    gameNode.put("homeTeam", node.path("HomeTeam").asText());
                    gameNode.put("awayTeam", node.path("AwayTeam").asText());
                    gameNode.put("homeScore", node.path("HomeTeamScore").asInt(0));
                    gameNode.put("awayScore", node.path("AwayTeamScore").asInt(0));
                    gameNode.put("status", node.path("Status").asText());

                    entityService.addItem(
                            GAME_ENTITY_NAME,
                            ENTITY_VERSION,
                            gameNode,
                            this::processGame
                    ).join();
                    Game game = new Game();
                    game.setGameId(gameNode.get("gameId").asText());
                    game.setDate(gameNode.get("date").asText());
                    game.setHomeTeam(gameNode.get("homeTeam").asText());
                    game.setAwayTeam(gameNode.get("awayTeam").asText());
                    game.setHomeScore(gameNode.get("homeScore").asInt());
                    game.setAwayScore(gameNode.get("awayScore").asInt());
                    game.setStatus(gameNode.get("status").asText());
                    games.add(game);
                }
                gamesByDate.put(request.getDate(), games);
                logger.info("Stored {} games for date {}", games.size(), request.getDate());
            } catch (Exception e) {
                logger.error("Error during fetch/store/notify for date {}: {}", request.getDate(), e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(Map.of("message", "Scores fetch started for " + request.getDate() + "."));
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<List<Game>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Retrieving games for date {}", date);
        List<Game> games = gamesByDate.getOrDefault(date, Collections.emptyList());
        return ResponseEntity.ok(games);
    }

    @PostMapping("/scheduler/config")
    public ResponseEntity<Map<String, String>> updateSchedulerConfig(@RequestBody @Valid SchedulerConfigRequest request) {
        logger.info("Updating scheduler config: cron='{}', enabled={}", request.getCronExpression(), request.isEnabled());
        schedulerConfig = new SchedulerConfig(request.getCronExpression(), request.isEnabled());
        // TODO: update actual scheduler config if implemented
        return ResponseEntity.ok(Map.of("message", "Scheduler updated."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ConfirmTokenRequest {
        @NotBlank
        private String token;
    }

    @Data
    public static class UnsubscribeRequest {
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
    public static class SchedulerConfigRequest {
        @NotBlank
        private String cronExpression;
        private boolean enabled;
    }

    @Data
    @AllArgsConstructor
    public static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    public static class Game {
        private String gameId;
        private String date;
        private String homeTeam;
        private String awayTeam;
        private int homeScore;
        private int awayScore;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class SubscribersPageResponse {
        private int page;
        private int size;
        private int totalPages;
        private int totalSubscribers;
        private List<String> subscribers;
    }

    @Data
    @AllArgsConstructor
    public static class SchedulerConfig {
        private String cronExpression;
        private boolean enabled;
    }
}