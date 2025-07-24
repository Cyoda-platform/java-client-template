package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.ScoreFetchJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // POST /controller/scoreFetchJob - create ScoreFetchJob entity
    @PostMapping("/scoreFetchJob")
    public ResponseEntity<?> createScoreFetchJob(@RequestBody ScoreFetchJob job) {
        try {
            if (job == null || job.getJobDate() == null || job.getStatus() == null || job.getStatus().isBlank()) {
                log.error("Invalid ScoreFetchJob input");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ScoreFetchJob input"));
            }

            // Set status RUNNING and triggeredAt before saving new entity
            job.setStatus("RUNNING");
            job.setTriggeredAt(Instant.now());

            CompletableFuture<UUID> idFuture = entityService.addItem("ScoreFetchJob", ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();

            log.info("ScoreFetchJob created with technicalId: {}", technicalId);

            processScoreFetchJob(job, technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createScoreFetchJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in createScoreFetchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /controller/scoreFetchJob/{id} - retrieve ScoreFetchJob by technicalId
    @GetMapping("/scoreFetchJob/{id}")
    public ResponseEntity<?> getScoreFetchJob(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("ScoreFetchJob", ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.get();
            if (item == null || item.isEmpty()) {
                log.error("ScoreFetchJob not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ScoreFetchJob not found"));
            }
            // Convert ObjectNode to ScoreFetchJob
            ScoreFetchJob job = entityService.getObjectMapper().treeToValue(item, ScoreFetchJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getScoreFetchJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in getScoreFetchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // POST /controller/subscriber - create Subscriber entity (local cache remains)
    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()
                    || subscriber.getStatus() == null || subscriber.getStatus().isBlank()) {
                log.error("Invalid Subscriber input");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Subscriber input"));
            }

            processSubscriber(subscriber);

            // Using local cache for Subscriber as per instructions
            // Generate id as UUID string
            String id = UUID.randomUUID().toString();

            // Return technicalId as id
            log.info("Subscriber created with ID: {}", id);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));

        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /controller/subscriber/{id} - get Subscriber (local cache, no persistence, so just error)
    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        try {
            // Cannot retrieve from external service, no local storage for subscribers
            // Return 404 as we don't store Subscribers persistently
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // POST /controller/gameScore - create GameScore entity
    @PostMapping("/gameScore")
    public ResponseEntity<?> createGameScore(@RequestBody GameScore gameScore) {
        try {
            if (gameScore == null || gameScore.getGameDate() == null || gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                    || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()
                    || gameScore.getHomeScore() == null || gameScore.getAwayScore() == null
                    || gameScore.getStatus() == null || gameScore.getStatus().isBlank()) {
                log.error("Invalid GameScore input");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid GameScore input"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem("GameScore", ENTITY_VERSION, gameScore);
            UUID technicalId = idFuture.get();

            log.info("GameScore created with technicalId: {}", technicalId);

            processGameScore(gameScore);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createGameScore", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in createGameScore", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /controller/gameScore/{id} - get GameScore by technicalId
    @GetMapping("/gameScore/{id}")
    public ResponseEntity<?> getGameScore(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("GameScore", ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.get();
            if (item == null || item.isEmpty()) {
                log.error("GameScore not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
            }
            GameScore gameScore = entityService.getObjectMapper().treeToValue(item, GameScore.class);
            return ResponseEntity.ok(gameScore);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getGameScore", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in getGameScore", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // Private helper: processScoreFetchJob business logic
    private void processScoreFetchJob(ScoreFetchJob job, UUID jobTechnicalId) {
        log.info("Processing ScoreFetchJob for date: {}", job.getJobDate());
        try {
            // 2. Fetch NBA scores asynchronously - simulate fetch here (in real, call external API)
            List<GameScore> fetchedScores = new ArrayList<>();
            GameScore gs = new GameScore();
            gs.setGameDate(job.getJobDate());
            gs.setHomeTeam("Team A");
            gs.setAwayTeam("Team B");
            gs.setHomeScore(100);
            gs.setAwayScore(98);
            gs.setStatus("RECEIVED");
            fetchedScores.add(gs);

            // 3. Save each fetched GameScore as immutable entity via entityService
            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (GameScore gsEntity : fetchedScores) {
                futures.add(entityService.addItem("GameScore", ENTITY_VERSION, gsEntity));
            }
            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // 4. Update job status to COMPLETED and completedAt timestamp by creating new entity version referencing previous
            ScoreFetchJob updatedJob = new ScoreFetchJob();
            updatedJob.setJobDate(job.getJobDate());
            updatedJob.setStatus("COMPLETED");
            updatedJob.setTriggeredAt(job.getTriggeredAt());
            updatedJob.setCompletedAt(Instant.now());

            // Add reference to previous entity (assuming a field previousTechnicalId)
            // If no such field, skip reference - prototype has no such field, so skip
            entityService.addItem("ScoreFetchJob", ENTITY_VERSION, updatedJob).get();

            // 5. Trigger notification process
            processGameScoreNotification(job.getJobDate());
        } catch (Exception e) {
            log.error("Exception in processScoreFetchJob", e);
        }
    }

    // Private helper: processSubscriber business logic
    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with email: {}", subscriber.getEmail());
        if (!subscriber.getEmail().contains("@")) {
            log.error("Invalid email format for subscriber: {}", subscriber.getEmail());
            return;
        }
        subscriber.setStatus("ACTIVE");
        subscriber.setSubscribedAt(Instant.now());
        log.info("Subscriber {} set to ACTIVE", subscriber.getEmail());
    }

    // Private helper: processGameScore business logic
    private void processGameScore(GameScore gameScore) {
        log.info("Processing GameScore for game: {} vs {} on {}", gameScore.getHomeTeam(), gameScore.getAwayTeam(), gameScore.getGameDate());
        // Placeholder for enrichment or validation logic
    }

    // Private helper: processGameScoreNotification business logic
    private void processGameScoreNotification(LocalDate jobDate) {
        log.info("Sending notifications for GameScores on date: {}", jobDate);
        try {
            Condition condition1 = Condition.of("$.gameDate", "EQUALS", jobDate.toString());
            Condition condition2 = Condition.of("$.status", "EQUALS", "RECEIVED");
            SearchConditionRequest condition = SearchConditionRequest.group("AND", condition1, condition2);

            CompletableFuture<ArrayNode> scoresFuture = entityService.getItemsByCondition("GameScore", ENTITY_VERSION, condition, true);
            ArrayNode scoresArray = scoresFuture.get();

            List<GameScore> scoresToNotify = new ArrayList<>();
            for (int i = 0; i < scoresArray.size(); i++) {
                ObjectNode obj = (ObjectNode) scoresArray.get(i);
                GameScore gs = entityService.getObjectMapper().treeToValue(obj, GameScore.class);
                scoresToNotify.add(gs);
            }

            StringBuilder summary = new StringBuilder("NBA Scores for " + jobDate + ":\n");
            for (GameScore gs : scoresToNotify) {
                summary.append(gs.getHomeTeam()).append(" ").append(gs.getHomeScore())
                        .append(" - ").append(gs.getAwayTeam()).append(" ").append(gs.getAwayScore())
                        .append("\n");
            }

            // Since subscribers are local cache only, simulate retrieving active subscribers by returning empty list
            // So notification simulation omitted (no persisted subscribers)

            log.info("Notification summary:\n{}", summary.toString());

            // Update GameScore statuses to PROCESSED by creating new entities with updated status
            for (GameScore gs : scoresToNotify) {
                GameScore updatedGs = new GameScore();
                updatedGs.setGameDate(gs.getGameDate());
                updatedGs.setHomeTeam(gs.getHomeTeam());
                updatedGs.setAwayTeam(gs.getAwayTeam());
                updatedGs.setHomeScore(gs.getHomeScore());
                updatedGs.setAwayScore(gs.getAwayScore());
                updatedGs.setStatus("PROCESSED");
                entityService.addItem("GameScore", ENTITY_VERSION, updatedGs).get();
            }
        } catch (Exception e) {
            log.error("Exception in processGameScoreNotification", e);
        }
    }
}