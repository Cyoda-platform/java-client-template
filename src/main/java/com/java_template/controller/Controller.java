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

    // Workflow functions removed in this version

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
            return entityService.addItem("Subscriber", ENTITY_VERSION, newSubscriber)
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

        return entityService.addItem("FetchScores", ENTITY_VERSION, fetchScoresEntity)
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