package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String NBA_SCORES_FETCH_JOB = "NbaScoresFetchJob";
    private static final String NBA_GAME = "NbaGame";

    public Controller(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    // ------------------ NbaScoresFetchJob Endpoints ------------------

    @PostMapping("/jobs/fetch-scores")
    public ResponseEntity<?> createNbaScoresFetchJob(@RequestBody Map<String, String> request) {
        try {
            String scheduledDateStr = request.get("scheduledDate");
            if (scheduledDateStr == null || scheduledDateStr.isBlank()) {
                logger.error("Missing or blank scheduledDate");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "scheduledDate is required"));
            }

            LocalDate scheduledDate;
            try {
                scheduledDate = LocalDate.parse(scheduledDateStr);
            } catch (Exception e) {
                logger.error("Invalid scheduledDate format: {}", scheduledDateStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "scheduledDate must be in YYYY-MM-DD format"));
            }

            ObjectNode jobNode = objectMapper.createObjectNode();
            jobNode.put("scheduledDate", scheduledDate.toString());
            jobNode.put("fetchTimeUTC", LocalTime.of(18, 0).toString()); // fixed 6:00 PM UTC
            jobNode.put("status", "PENDING");
            jobNode.putNull("summary");

            CompletableFuture<UUID> idFuture = entityService.addItem(NBA_SCORES_FETCH_JOB, ENTITY_VERSION, jobNode);
            UUID technicalId = idFuture.get();

            // Trigger processing asynchronously
            processNbaScoresFetchJobAsync(technicalId, scheduledDate);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating NbaScoresFetchJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jobs/fetch-scores/{technicalId}")
    public ResponseEntity<?> getNbaScoresFetchJobById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(NBA_SCORES_FETCH_JOB, ENTITY_VERSION, id);
            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NbaScoresFetchJob not found"));
            }
            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching NbaScoresFetchJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------ Subscriber Endpoints (local cache unchanged) ------------------

    private final Map<String, Subscriber> subscriberCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private int subscriberIdCounter = 1;

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.isBlank()) {
                logger.error("Missing or blank email");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "email is required"));
            }
            if (!isValidEmail(email)) {
                logger.error("Invalid email format: {}", email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid email format"));
            }

            // Check uniqueness
            boolean exists = subscriberCache.values().stream()
                    .anyMatch(sub -> sub.getEmail().equalsIgnoreCase(email) && "ACTIVE".equals(sub.getStatus()));
            if (exists) {
                logger.error("Email already subscribed: {}", email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "email already subscribed"));
            }

            Subscriber subscriber = new Subscriber();
            String id = "subscriber-" + subscriberIdCounter++;
            subscriber.setId(id);
            subscriber.setEmail(email);
            subscriber.setSubscriptionDate(new Date());
            subscriber.setStatus("ACTIVE");

            subscriberCache.put(id, subscriber);

            // Trigger processing
            processSubscriber(subscriber);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating Subscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriberById(@PathVariable String id) {
        try {
            Subscriber subscriber = subscriberCache.get(id);
            if (subscriber == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
            }
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching Subscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------ NbaGame Endpoints ------------------

    @GetMapping("/games/{date}")
    public ResponseEntity<?> getGamesByDate(@PathVariable String date) {
        try {
            LocalDate queryDate;
            try {
                queryDate = LocalDate.parse(date);
            } catch (Exception e) {
                logger.error("Invalid date format: {}", date);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "date must be in YYYY-MM-DD format"));
            }

            Condition dateCondition = Condition.of("$.gameDate", "EQUALS", queryDate.toString());
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", dateCondition);

            CompletableFuture<ArrayNode> gamesFuture = entityService.getItemsByCondition(NBA_GAME, ENTITY_VERSION, conditionRequest, true);
            ArrayNode gamesArray = gamesFuture.get();

            List<ObjectNode> result = new ArrayList<>();
            for (JsonNode node : gamesArray) {
                result.add((ObjectNode) node);
            }

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching games by date: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/games/all")
    public ResponseEntity<?> getAllGames(@RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        try {
            CompletableFuture<ArrayNode> gamesFuture = entityService.getItems(NBA_GAME, ENTITY_VERSION);
            ArrayNode gamesArray = gamesFuture.get();

            List<ObjectNode> resultList = new ArrayList<>();
            for (JsonNode node : gamesArray) {
                resultList.add((ObjectNode) node);
            }

            if (page != null && size != null && page > 0 && size > 0) {
                int fromIndex = (page - 1) * size;
                if (fromIndex >= resultList.size()) {
                    return ResponseEntity.ok(Collections.emptyList());
                }
                int toIndex = Math.min(fromIndex + size, resultList.size());
                return ResponseEntity.ok(resultList.subList(fromIndex, toIndex));
            }
            return ResponseEntity.ok(resultList);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching all games: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------ Processing Methods ------------------

    private void processNbaScoresFetchJobAsync(UUID jobTechnicalId, LocalDate scheduledDate) {
        // Run asynchronously - fire and forget
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Processing NbaScoresFetchJob with technicalId: {}", jobTechnicalId);

                if (scheduledDate.isAfter(LocalDate.now())) {
                    updateJobStatus(jobTechnicalId, "FAILED", "Scheduled date cannot be in the future");
                    logger.error("Job {} failed: scheduledDate in future", jobTechnicalId);
                    return;
                }

                updateJobStatus(jobTechnicalId, "IN_PROGRESS", null);

                String apiKey = "test"; // Replace with valid API key or config
                String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s", scheduledDate, apiKey);

                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                if (root.isArray()) {
                    int gamesCount = 0;
                    List<ObjectNode> newGames = new ArrayList<>();
                    for (JsonNode node : root) {
                        ObjectNode gameNode = objectMapper.createObjectNode();
                        gameNode.put("gameDate", scheduledDate.toString());
                        gameNode.put("homeTeam", node.path("HomeTeam").asText());
                        if (!node.path("HomeTeamScore").isNull())
                            gameNode.put("homeScore", node.path("HomeTeamScore").asInt());
                        else
                            gameNode.putNull("homeScore");
                        gameNode.put("awayTeam", node.path("AwayTeam").asText());
                        if (!node.path("AwayTeamScore").isNull())
                            gameNode.put("awayScore", node.path("AwayTeamScore").asInt());
                        else
                            gameNode.putNull("awayScore");
                        gameNode.put("status", node.path("Status").asText(null));
                        newGames.add(gameNode);
                        gamesCount++;
                    }

                    // Add all games
                    if (!newGames.isEmpty()) {
                        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(NBA_GAME, ENTITY_VERSION, newGames);
                        List<UUID> technicalIds = idsFuture.get();
                        logger.info("Added {} games with technical IDs: {}", technicalIds.size(), technicalIds);
                    }

                    updateJobStatus(jobTechnicalId, "COMPLETED", "Fetched " + gamesCount + " games for " + scheduledDate);

                    // Notify subscribers
                    StringBuilder summaryBuilder = new StringBuilder();
                    summaryBuilder.append("NBA Scores for ").append(scheduledDate).append(":\n");
                    for (JsonNode node : root) {
                        summaryBuilder.append(node.path("HomeTeam").asText()).append(" ")
                                .append(node.path("HomeTeamScore").isNull() ? "-" : node.path("HomeTeamScore").asInt())
                                .append(" - ")
                                .append(node.path("AwayTeamScore").isNull() ? "-" : node.path("AwayTeamScore").asInt())
                                .append(" ")
                                .append(node.path("AwayTeam").asText()).append("\n");
                    }

                    subscriberCache.values().stream()
                            .filter(s -> "ACTIVE".equals(s.getStatus()))
                            .forEach(s -> {
                                logger.info("Sending email to {} with summary:\n{}", s.getEmail(), summaryBuilder.toString());
                                // Implement real email sending here if available
                            });

                } else {
                    updateJobStatus(jobTechnicalId, "FAILED", "Unexpected API response format");
                    logger.error("Unexpected API response format for job {}", jobTechnicalId);
                }
            } catch (Exception e) {
                try {
                    updateJobStatus(jobTechnicalId, "FAILED", "Exception during fetch: " + e.getMessage());
                } catch (Exception ex) {
                    logger.error("Failed to update job status for {}: {}", jobTechnicalId, ex.getMessage());
                }
                logger.error("Error processing job {}: {}", jobTechnicalId, e.getMessage());
            }
        });
    }

    private void updateJobStatus(UUID jobTechnicalId, String status, String summary) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem(NBA_SCORES_FETCH_JOB, ENTITY_VERSION, jobTechnicalId);
        ObjectNode oldJob = jobFuture.get();
        if (oldJob == null) {
            logger.error("Job not found for status update: {}", jobTechnicalId);
            return;
        }
        ObjectNode newJob = oldJob.deepCopy();
        newJob.put("status", status);
        if (summary != null) {
            newJob.put("summary", summary);
        } else {
            newJob.putNull("summary");
        }
        newJob.put("scheduledDate", oldJob.path("scheduledDate").asText());
        newJob.put("fetchTimeUTC", oldJob.path("fetchTimeUTC").asText());
        // Add reference to previous entity if needed, but skipping as not specified

        entityService.addItem(NBA_SCORES_FETCH_JOB, ENTITY_VERSION, newJob).get();
    }

    private void processSubscriber(Subscriber subscriber) {
        logger.info("Processing Subscriber with ID: {}", subscriber.getId());
        // No additional processing currently
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return Pattern.compile(emailRegex).matcher(email).matches();
    }

    // ------------------ Subscriber class (local cache) ------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String id;
        private String email;
        private Date subscriptionDate;
        private String status;
    }
}