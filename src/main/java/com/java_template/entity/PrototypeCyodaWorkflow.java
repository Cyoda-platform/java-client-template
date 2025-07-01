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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscription {
        private String email;
        private OffsetDateTime subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GameScore {
        private LocalDate date;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresTask {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    static class FetchScoresRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    // Workflow function for Subscription entity.
    // Normalize email to lowercase before persisting.
    private CompletableFuture<Object> processSubscription(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        if (objNode.has("email") && !objNode.get("email").isNull()) {
            String email = objNode.get("email").asText();
            objNode.put("email", email.toLowerCase());
        }
        return CompletableFuture.completedFuture(objNode);
    }

    // Workflow function for GameScore entity.
    // Uppercase status before persisting.
    private CompletableFuture<Object> processGameScore(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        if (objNode.has("status") && !objNode.get("status").isNull()) {
            String status = objNode.get("status").asText();
            objNode.put("status", status.toUpperCase());
        }
        return CompletableFuture.completedFuture(objNode);
    }

    // Workflow function for FetchScoresTask entity.
    // Performs external fetch, stores GameScore entities, and notifies subscribers.
    // Runs asynchronously right before persisting the FetchScoresTask entity.
    private CompletableFuture<Object> processFetchScoresTask(Object entity) {
        ObjectNode taskNode = (ObjectNode) entity;
        String dateStr = taskNode.has("date") ? taskNode.get("date").asText() : null;
        if (dateStr == null || dateStr.isBlank()) {
            logger.warn("FetchScoresTask missing valid date, skipping fetch");
            return CompletableFuture.completedFuture(taskNode);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                LocalDate date = LocalDate.parse(dateStr);
                logger.info("Starting fetch/store/notify pipeline for date {}", date);

                // Fetch external scores
                String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date + "?key=test";
                String raw = restTemplate.getForObject(new URI(url), String.class);
                JsonNode arr = objectMapper.readTree(raw);
                if (!arr.isArray()) {
                    logger.warn("Expected array JSON but got {}", arr.getNodeType());
                    return taskNode;
                }

                // For each fetched GameScore, add entity with workflow processGameScore
                List<CompletableFuture<UUID>> futures = new ArrayList<>();
                for (JsonNode n : arr) {
                    String home = n.path("HomeTeam").asText(null);
                    String away = n.path("AwayTeam").asText(null);
                    Integer hScore = n.path("HomeTeamScore").isInt() ? n.path("HomeTeamScore").asInt() : null;
                    Integer aScore = n.path("AwayTeamScore").isInt() ? n.path("AwayTeamScore").asInt() : null;
                    String stat = n.path("Status").asText(null);
                    if (home == null || away == null) continue;

                    GameScore gs = new GameScore(date, home, away, hScore, aScore, stat);
                    CompletableFuture<UUID> f = entityService.addItem(
                            "GameScore",
                            ENTITY_VERSION,
                            gs,
                            this::processGameScore
                    );
                    futures.add(f);
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                logger.info("Stored {} GameScore entities for date {}", futures.size(), date);

                // Notify subscribers with the new scores
                notifySubscribers(date);

            } catch (Exception ex) {
                logger.error("Error in processFetchScoresTask workflow", ex);
            }
            return taskNode;
        });
    }

    // Notify all subscribers with scores summary for the given date.
    private void notifySubscribers(LocalDate date) throws Exception {
        StringBuilder sb = new StringBuilder("Daily NBA Scores for " + date + ":\n");

        Condition cond = Condition.of("$.date", "EQUALS", date.toString());
        SearchConditionRequest searchCond = SearchConditionRequest.group("AND", cond);
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> arrFuture =
                entityService.getItemsByCondition("GameScore", ENTITY_VERSION, searchCond);
        com.fasterxml.jackson.databind.node.ArrayNode arr = arrFuture.get();

        List<GameScore> games = new ArrayList<>();
        for (JsonNode node : arr) {
            GameScore gs = objectMapper.treeToValue(node, GameScore.class);
            games.add(gs);
        }

        games.forEach(g -> sb.append(String.format("%s vs %s: %d-%d\n",
                g.getAwayTeam(), g.getHomeTeam(),
                Optional.ofNullable(g.getAwayScore()).orElse(-1),
                Optional.ofNullable(g.getHomeScore()).orElse(-1))));

        String msg = sb.toString();

        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> subsFuture =
                entityService.getItems("Subscription", ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode subsArr = subsFuture.get();

        for (JsonNode subNode : subsArr) {
            if (subNode.has("email")) {
                String email = subNode.get("email").asText();
                logger.info("Sending email to {}: \n{}", email, msg); // TODO: Replace with actual email send
            }
        }
        logger.info("Notifications sent to subscribers");
    }

    // ========== CONTROLLER ENDPOINTS ==========

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscribeRequest request) throws Exception {
        String email = request.getEmail().toLowerCase();

        // Check for duplicate subscription
        Condition cond = Condition.of("$.email", "EQUALS", email);
        SearchConditionRequest searchCond = SearchConditionRequest.group("AND", cond);
        CompletableFuture<List<UUID>> existingIdsFuture = entityService.getItemsByCondition("Subscription", ENTITY_VERSION, searchCond)
                .thenApply(arr -> {
                    List<UUID> ids = new ArrayList<>();
                    for (JsonNode node : arr) {
                        if (node.has("technicalId")) {
                            ids.add(UUID.fromString(node.get("technicalId").asText()));
                        }
                    }
                    return ids;
                });
        List<UUID> existingIds = existingIdsFuture.get();

        if (!existingIds.isEmpty()) {
            logger.info("Duplicate subscription attempt for email: {}", email);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Subscription already exists."));
        }

        Subscription sub = new Subscription(email, OffsetDateTime.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Subscription",
                ENTITY_VERSION,
                sub,
                this::processSubscription
        );
        UUID id = idFuture.get();
        logger.info("New subscription added: {}", email);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Subscription added."));
    }

    @GetMapping("/subscribers")
    public ResponseEntity<Map<String, List<String>>> getSubscribers() throws Exception {
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> arrFuture = entityService.getItems("Subscription", ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode arr = arrFuture.get();

        List<String> emails = new ArrayList<>();
        for (JsonNode node : arr) {
            if (node.has("email")) {
                emails.add(node.get("email").asText());
            }
        }
        logger.info("Returning {} subscribers", emails.size());
        return ResponseEntity.ok(Map.of("subscribers", emails));
    }

    // Replaced /fetch-scores endpoint to just add a FetchScoresTask entity.
    // The workflow processFetchScoresTask will run asynchronously and perform the fetch/store/notify.
    @PostMapping("/fetch-scores")
    public ResponseEntity<Map<String, String>> fetchScores(@RequestBody(required = false) @Valid FetchScoresRequest request) throws Exception {
        String dateStr = (request != null && request.getDate() != null && !request.getDate().isBlank())
                ? request.getDate()
                : LocalDate.now().toString();

        FetchScoresTask task = new FetchScoresTask(dateStr);

        CompletableFuture<UUID> taskIdFuture = entityService.addItem(
                "FetchScoresTask",
                ENTITY_VERSION,
                task,
                this::processFetchScoresTask
        );

        UUID taskId = taskIdFuture.get();
        logger.info("FetchScoresTask created with ID {} for date {}", taskId, dateStr);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Scores fetch and notification task created."
        ));
    }

    @GetMapping("/games/all")
    public ResponseEntity<Map<String, Object>> getAllGames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size,
            @RequestParam(required = false) @Size(min = 1) String team,
            @RequestParam(required = false) @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dateFrom,
            @RequestParam(required = false) @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dateTo
    ) throws Exception {
        logger.info("Fetching games page={}, size={}, team={}, dateFrom={}, dateTo={}", page, size, team, dateFrom, dateTo);
        LocalDate from = null, to = null;
        try {
            if (dateFrom != null) from = LocalDate.parse(dateFrom);
            if (dateTo != null) to = LocalDate.parse(dateTo);
        } catch (Exception ex) {
            logger.error("Invalid dateFrom/dateTo format");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be YYYY-MM-DD");
        }
        if (team != null) {
            Condition condHome = Condition.of("$.homeTeam", "ICONTAINS", team);
            Condition condAway = Condition.of("$.awayTeam", "ICONTAINS", team);
            SearchConditionRequest teamSearchCond = SearchConditionRequest.group("OR", condHome, condAway);
            List<Condition> dateConds = new ArrayList<>();
            if (from != null) dateConds.add(Condition.of("$.date", "GREATER_OR_EQUAL", from.toString()));
            if (to != null) dateConds.add(Condition.of("$.date", "LESS_OR_EQUAL", to.toString()));
            List<Condition> combinedConditions = new ArrayList<>();
            combinedConditions.addAll(teamSearchCond.getConditions());
            combinedConditions.addAll(dateConds);
            SearchConditionRequest finalCond = SearchConditionRequest.group("AND", combinedConditions.toArray(new Condition[0]));
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> arrFuture = entityService.getItemsByCondition("GameScore", ENTITY_VERSION, finalCond);
            com.fasterxml.jackson.databind.node.ArrayNode arr = arrFuture.get();
            List<GameScore> filtered = new ArrayList<>();
            for (JsonNode node : arr) {
                filtered.add(objectMapper.treeToValue(node, GameScore.class));
            }
            int fromIdx = page * size;
            int toIdx = Math.min(fromIdx + size, filtered.size());
            if (fromIdx > filtered.size()) {
                fromIdx = filtered.size();
                toIdx = filtered.size();
            }
            List<GameScore> content = filtered.subList(fromIdx, toIdx);
            Map<String, Object> resp = new HashMap<>();
            resp.put("games", content);
            resp.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "totalPages", (int) Math.ceil((double) filtered.size() / size),
                    "totalElements", filtered.size()
            ));
            return ResponseEntity.ok(resp);
        } else {
            List<Condition> conds = new ArrayList<>();
            if (from != null) conds.add(Condition.of("$.date", "GREATER_OR_EQUAL", from.toString()));
            if (to != null) conds.add(Condition.of("$.date", "LESS_OR_EQUAL", to.toString()));
            SearchConditionRequest cond;
            if (conds.isEmpty()) {
                cond = null; // no condition = fetch all
            } else {
                cond = SearchConditionRequest.group("AND", conds.toArray(new Condition[0]));
            }
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> arrFuture;
            if (cond != null) {
                arrFuture = entityService.getItemsByCondition("GameScore", ENTITY_VERSION, cond);
            } else {
                arrFuture = entityService.getItems("GameScore", ENTITY_VERSION);
            }
            com.fasterxml.jackson.databind.node.ArrayNode arr = arrFuture.get();
            List<GameScore> filtered = new ArrayList<>();
            for (JsonNode node : arr) {
                filtered.add(objectMapper.treeToValue(node, GameScore.class));
            }
            int fromIdx = page * size;
            int toIdx = Math.min(fromIdx + size, filtered.size());
            if (fromIdx > filtered.size()) {
                fromIdx = filtered.size();
                toIdx = filtered.size();
            }
            List<GameScore> content = filtered.subList(fromIdx, toIdx);
            Map<String, Object> resp = new HashMap<>();
            resp.put("games", content);
            resp.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "totalPages", (int) Math.ceil((double) filtered.size() / size),
                    "totalElements", filtered.size()
            ));
            return ResponseEntity.ok(resp);
        }
    }

    @GetMapping("/games/{date}")
    public ResponseEntity<Map<String, List<GameScore>>> getGamesByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date
    ) throws Exception {
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (Exception ex) {
            logger.error("Invalid date format: {}", date);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in YYYY-MM-DD format");
        }
        Condition cond = Condition.of("$.date", "EQUALS", d.toString());
        SearchConditionRequest searchCond = SearchConditionRequest.group("AND", cond);
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> arrFuture = entityService.getItemsByCondition("GameScore", ENTITY_VERSION, searchCond);
        com.fasterxml.jackson.databind.node.ArrayNode arr = arrFuture.get();
        List<GameScore> result = new ArrayList<>();
        for (JsonNode node : arr) {
            result.add(objectMapper.treeToValue(node, GameScore.class));
        }
        logger.info("Returning {} games for date {}", result.size(), date);
        return ResponseEntity.ok(Map.of("games", result));
    }

    // ========== EXCEPTION HANDLERS ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        ));
    }
}