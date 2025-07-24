package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DailyScoresJob;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@AllArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    // Local counters remain for temporary ID generation for logs or internal use
    private final AtomicLong dailyScoresJobIdCounter = new AtomicLong(1);
    private final AtomicLong gameScoreIdCounter = new AtomicLong(1);

    // --- DailyScoresJob Endpoints ---

    @PostMapping("/dailyScoresJob")
    public ResponseEntity<?> createDailyScoresJob(@RequestBody DailyScoresJob jobRequest) {
        try {
            if (jobRequest == null || jobRequest.getDate() == null || jobRequest.getDate().isBlank()) {
                logger.error("Invalid DailyScoresJob creation request: missing or blank date");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Date is required and cannot be blank"));
            }

            jobRequest.setStatus("PENDING");
            jobRequest.setCreatedAt(java.time.Instant.now().toString());
            jobRequest.setCompletedAt(null);

            // Use entityService to add item
            CompletableFuture<UUID> idFuture = entityService.addItem("DailyScoresJob", ENTITY_VERSION, jobRequest);
            UUID technicalId = idFuture.get();

            logger.info("Created DailyScoresJob with technicalId: {}", technicalId);

            processDailyScoresJob(jobRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument in createDailyScoresJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createDailyScoresJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/dailyScoresJob/{id}")
    public ResponseEntity<?> getDailyScoresJobById(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("DailyScoresJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("DailyScoresJob with technicalId {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DailyScoresJob not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID format for DailyScoresJob id {}: {}", id, iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException ee) {
            logger.error("Execution error retrieving DailyScoresJob {}: {}", id, ee.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DailyScoresJob not found"));
        } catch (Exception e) {
            logger.error("Error retrieving DailyScoresJob {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriberRequest) {
        try {
            if (subscriberRequest == null || subscriberRequest.getEmail() == null || subscriberRequest.getEmail().isBlank()) {
                logger.error("Invalid Subscriber creation request: missing or blank email");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required and cannot be blank"));
            }

            subscriberRequest.setSubscribedAt(java.time.Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem("Subscriber", ENTITY_VERSION, subscriberRequest);
            UUID technicalId = idFuture.get();

            logger.info("Created Subscriber with technicalId: {}", technicalId);

            processSubscriber(subscriberRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument in createSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createSubscriber: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriberById(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Subscriber", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber with technicalId {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID format for Subscriber id {}: {}", id, iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException ee) {
            logger.error("Execution error retrieving Subscriber {}: {}", id, ee.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        } catch (Exception e) {
            logger.error("Error retrieving Subscriber {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- GameScore Endpoints ---

    @GetMapping("/gameScore/{id}")
    public ResponseEntity<?> getGameScoreById(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("GameScore", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("GameScore with technicalId {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID format for GameScore id {}: {}", id, iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException ee) {
            logger.error("Execution error retrieving GameScore {}: {}", id, ee.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
        } catch (Exception e) {
            logger.error("Error retrieving GameScore {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Note: No POST endpoint for GameScore because these are created immutably by the job process

    // --- Process Methods ---

    private void processDailyScoresJob(DailyScoresJob job) {
        logger.info("Processing DailyScoresJob for date: {}", job.getDate());

        // Validate date is not in the future
        try {
            java.time.LocalDate jobDate = java.time.LocalDate.parse(job.getDate());
            if (jobDate.isAfter(java.time.LocalDate.now())) {
                job.setStatus("FAILED");
                job.setCompletedAt(java.time.Instant.now().toString());
                logger.error("DailyScoresJob date {} is in the future", job.getDate());
                return;
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setCompletedAt(java.time.Instant.now().toString());
            logger.error("Invalid date format for DailyScoresJob: {}", job.getDate());
            return;
        }

        try {
            // Simulate fetching NBA scores from external API asynchronously (simplified here)
            List<GameScore> fetchedScores = fetchNbaScores(job.getDate());

            // Persist GameScore entities immutably using entityService
            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (GameScore score : fetchedScores) {
                // Use entityService.addItem
                CompletableFuture<UUID> future = entityService.addItem("GameScore", ENTITY_VERSION, score);
                futures.add(future);
                processGameScore(score);
            }
            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // Retrieve all subscribers and send email notifications
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems("Subscriber", ENTITY_VERSION);
            ArrayNode subscribers = subscribersFuture.get();

            for (var subscriberNode : subscribers) {
                String email = null;
                if (subscriberNode.has("email") && !subscriberNode.get("email").isNull()) {
                    email = subscriberNode.get("email").asText();
                }
                if (email != null) {
                    sendEmailNotification(email, job.getDate(), fetchedScores);
                }
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(java.time.Instant.now().toString());
            logger.info("Completed processing DailyScoresJob for date: {}", job.getDate());

        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setCompletedAt(java.time.Instant.now().toString());
            logger.error("Error processing DailyScoresJob: {}", ex.getMessage(), ex);
        }
    }

    private void processSubscriber(Subscriber subscriber) {
        logger.info("Processing Subscriber with email: {}", subscriber.getEmail());

        // Validate email format simple check
        if (!subscriber.getEmail().contains("@")) {
            logger.error("Invalid email format for subscriber: {}", subscriber.getEmail());
        }
        // No further processing needed immediately
    }

    private void processGameScore(GameScore gameScore) {
        logger.info("Processing GameScore: {} vs {} on {}", gameScore.getHomeTeam(),
                gameScore.getAwayTeam(), gameScore.getDate());

        // Optional: Validate scores and gameStatus consistency
        if (gameScore.getHomeScore() < 0 || gameScore.getAwayScore() < 0) {
            logger.error("Invalid score values for GameScore: {} vs {}", gameScore.getHomeTeam(), gameScore.getAwayTeam());
        }
    }

    // --- Helper Methods ---

    private List<GameScore> fetchNbaScores(String date) {
        // Simulated API fetch. In real implementation, call external API asynchronously.
        logger.info("Fetching NBA scores for date: {}", date);

        // Dummy data for prototype
        GameScore gs1 = new GameScore();
        gs1.setDate(date);
        gs1.setHomeTeam("Lakers");
        gs1.setAwayTeam("Warriors");
        gs1.setHomeScore(102);
        gs1.setAwayScore(99);
        gs1.setGameStatus("Completed");

        GameScore gs2 = new GameScore();
        gs2.setDate(date);
        gs2.setHomeTeam("Nets");
        gs2.setAwayTeam("Celtics");
        gs2.setHomeScore(110);
        gs2.setAwayScore(115);
        gs2.setGameStatus("Completed");

        return List.of(gs1, gs2);
    }

    private void sendEmailNotification(String email, String date, List<GameScore> gameScores) {
        // Simulated email sending. Real implementation would use JavaMailSender or similar.
        logger.info("Sending email notification to {} for NBA games on {}", email, date);
        // Construct email content from gameScores if needed
    }
}