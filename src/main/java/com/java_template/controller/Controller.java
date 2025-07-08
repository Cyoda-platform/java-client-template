package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("cyoda-entity-prototype")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    private static final String ENTITY_NAME = "CyodaEntity";
    private static final String EXTERNAL_API_TEMPLATE =
            "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    static class SubscribeRequest {
        @Email
        @NotBlank
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    static class FetchScoresRequest {
        @NotNull
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    static class ApiResponse {
        private String status;
        private String message;

        public ApiResponse() {
        }

        public ApiResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
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

        List<ObjectNode> gameNodes = new ArrayList<>();
        if (apiResponse != null && apiResponse.isArray()) {
            apiResponse.forEach(node -> {
                if (!node.isObject()) return;
                ObjectNode gameNode = objectMapper.createObjectNode();
                gameNode.put("date", request.getDate());
                gameNode.put("homeTeam", node.path("HomeTeam").asText(null));
                gameNode.put("awayTeam", node.path("AwayTeam").asText(null));
                if (node.hasNonNull("HomeTeamScore")) {
                    gameNode.put("homeScore", node.get("HomeTeamScore").asInt());
                } else {
                    gameNode.putNull("homeScore");
                }
                if (node.hasNonNull("AwayTeamScore")) {
                    gameNode.put("awayScore", node.get("AwayTeamScore").asInt());
                } else {
                    gameNode.putNull("awayScore");
                }
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
            CompletableFuture<List<UUID>> addFut = entityService.addItems(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    gameNodes
            );
            addFut.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error storing games in EntityService", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store games");
        }

        logger.info("Stored {} games for {}", gameNodes.size(), request.getDate());
        return ResponseEntity.ok(new ApiResponse("success", "Scores fetched and stored successfully."));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse> subscribe(@RequestBody @Valid SubscribeRequest request) throws ExecutionException, InterruptedException {
        ObjectNode subNode = objectMapper.createObjectNode();
        subNode.put("email", request.getEmail());

        CompletableFuture<UUID> addFut = entityService.addItem("Subscriber", ENTITY_VERSION, subNode);
        addFut.get();
        logger.info("Subscribed {}", request.getEmail());
        return ResponseEntity.ok(new ApiResponse("success", "Email subscribed successfully."));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<ApiResponse> deleteSubscription(@RequestParam @Email @NotBlank String email) throws ExecutionException, InterruptedException {
        String emailLower = email.toLowerCase(Locale.ROOT);
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
        List<CompletableFuture<UUID>> deleteFutures = new ArrayList<>();
        subsArray.forEach(node -> {
            if (node.hasNonNull("technicalId")) {
                try {
                    UUID techId = UUID.fromString(node.get("technicalId").asText());
                    deleteFutures.add(entityService.deleteItem("Subscriber", ENTITY_VERSION, techId));
                } catch (IllegalArgumentException ignored) { }
            }
        });
        for (CompletableFuture<UUID> f : deleteFutures) {
            f.get();
        }
        logger.info("Unsubscribed {}", email);
        return ResponseEntity.ok(new ApiResponse("success", "Email unsubscribed successfully."));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<String>> getSubscribers() throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND");
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