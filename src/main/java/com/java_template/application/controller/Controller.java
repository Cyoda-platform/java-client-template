package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.NbaScoreFetchJob;
import com.java_template.application.entity.Subscription;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Keep subscription cache locally as minor entity (per instruction)
    private final Map<String, Subscription> subscriptionCache = new HashMap<>();
    private long subscriptionIdCounter = 1L;

    // -------------------- NbaScoreFetchJob Endpoints --------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createNbaScoreFetchJob(@RequestBody Map<String, String> request) {
        try {
            String scheduledDateStr = request.get("scheduledDate");
            if (scheduledDateStr == null || scheduledDateStr.isBlank()) {
                logger.error("Scheduled date is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "scheduledDate is required"));
            }
            NbaScoreFetchJob job = new NbaScoreFetchJob();
            try {
                job.setScheduledDate(LocalDate.parse(scheduledDateStr));
            } catch (Exception e) {
                logger.error("Invalid scheduledDate format: {}", scheduledDateStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "scheduledDate must be in YYYY-MM-DD format"));
            }
            job.setStatus("PENDING");
            job.setCreatedAt(LocalDateTime.now());

            // Persist job via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem("NbaScoreFetchJob", ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();
            String technicalIdStr = technicalId.toString();
            job.setJobId(technicalIdStr);

            processNbaScoreFetchJob(job, technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createNbaScoreFetchJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating NbaScoreFetchJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getNbaScoreFetchJob(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("NbaScoreFetchJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("NbaScoreFetchJob not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for NbaScoreFetchJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid id format"));
        } catch (Exception e) {
            logger.error("Error fetching NbaScoreFetchJob {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // -------------------- GameScore Endpoints --------------------

    @GetMapping("/games/{id}")
    public ResponseEntity<?> getGameScore(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("GameScore", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("GameScore not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for GameScore id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid id format"));
        } catch (Exception e) {
            logger.error("Error fetching GameScore {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // No POST endpoint for GameScore as per original design and EDA principle.

    // -------------------- Subscription Endpoints --------------------

    @PostMapping("/subscriptions")
    public ResponseEntity<?> createSubscription(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            String notificationType = request.get("notificationType");
            String channel = request.get("channel");
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "userId is required"));
            }
            if (notificationType == null || notificationType.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "notificationType is required"));
            }
            if (channel == null || channel.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "channel is required"));
            }
            String team = request.getOrDefault("team", null);

            Subscription subscription = new Subscription();
            String technicalId = "sub_" + subscriptionIdCounter++;
            subscription.setSubscriptionId(technicalId);
            subscription.setUserId(userId);
            subscription.setNotificationType(notificationType);
            subscription.setChannel(channel);
            subscription.setTeam(team);
            subscription.setStatus("ACTIVE");
            subscription.setCreatedAt(LocalDateTime.now());

            // Keep subscription locally per instruction (minor entity)
            subscriptionCache.put(technicalId, subscription);

            processSubscription(subscription);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createSubscription: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating Subscription: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscriptions/{id}")
    public ResponseEntity<?> getSubscription(@PathVariable("id") String id) {
        try {
            Subscription subscription = subscriptionCache.get(id);
            if (subscription == null) {
                logger.error("Subscription not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscription not found"));
            }
            return ResponseEntity.ok(subscription);
        } catch (Exception e) {
            logger.error("Error fetching Subscription {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // -------------------- Process Methods --------------------

    private void processNbaScoreFetchJob(NbaScoreFetchJob job, UUID technicalId) {
        logger.info("Processing NbaScoreFetchJob with ID: {}", technicalId);
        try {
            job.setStatus("RUNNING");
            // TODO: Update status to RUNNING in EntityService - no update method, so skip

            // Simulate external NBA API call to fetch scores for scheduledDate
            List<GameScore> fetchedGameScores = fetchGameScoresFromExternalApi(job.getScheduledDate());

            // Persist each GameScore entity via EntityService
            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (GameScore gs : fetchedGameScores) {
                gs.setGameId(null); // clear any local ID before persisting
                futures.add(entityService.addItem("GameScore", ENTITY_VERSION, gs));
            }

            // Wait for all to complete
            List<UUID> technicalIds = new ArrayList<>();
            for (CompletableFuture<UUID> f : futures) {
                technicalIds.add(f.get());
            }

            // Set IDs back for further processing and notifications
            for (int i = 0; i < fetchedGameScores.size(); i++) {
                fetchedGameScores.get(i).setGameId(technicalIds.get(i).toString());
                processGameScore(fetchedGameScores.get(i));
            }

            job.setStatus("COMPLETED");
            // TODO: Update status to COMPLETED in EntityService - no update method, so skip

            logger.info("NbaScoreFetchJob {} completed successfully with {} games fetched.", technicalId, fetchedGameScores.size());

            // Trigger notifications for each GameScore and active subscriptions
            for (GameScore gs : fetchedGameScores) {
                triggerNotifications(gs);
            }
        } catch (Exception e) {
            logger.error("Failed to process NbaScoreFetchJob {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            // TODO: Update status to FAILED in EntityService - no update method, so skip
        }
    }

    private List<GameScore> fetchGameScoresFromExternalApi(LocalDate date) {
        logger.info("Fetching NBA game scores for date {}", date);
        List<GameScore> gameScores = new ArrayList<>();

        GameScore gs1 = new GameScore();
        gs1.setGameDate(date);
        gs1.setHomeTeam("Lakers");
        gs1.setAwayTeam("Warriors");
        gs1.setHomeScore(102);
        gs1.setAwayScore(99);
        gs1.setStatus("COMPLETED");
        gameScores.add(gs1);

        GameScore gs2 = new GameScore();
        gs2.setGameDate(date);
        gs2.setHomeTeam("Celtics");
        gs2.setAwayTeam("Bulls");
        gs2.setHomeScore(110);
        gs2.setAwayScore(105);
        gs2.setStatus("COMPLETED");
        gameScores.add(gs2);

        return gameScores;
    }

    private void processGameScore(GameScore gameScore) {
        logger.info("Processing GameScore with ID: {}", gameScore.getGameId());
        if (gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()) {
            logger.error("Invalid GameScore data: missing team names");
            return;
        }
        if (gameScore.getHomeScore() == null || gameScore.getAwayScore() == null) {
            logger.error("Invalid GameScore data: missing scores");
            return;
        }
        logger.info("GameScore {} processed successfully", gameScore.getGameId());
    }

    private void processSubscription(Subscription subscription) {
        logger.info("Processing Subscription with ID: {}", subscription.getSubscriptionId());
        if (subscription.getUserId() == null || subscription.getUserId().isBlank()) {
            logger.error("Subscription userId is invalid");
            return;
        }
        if (subscription.getNotificationType() == null || subscription.getNotificationType().isBlank()) {
            logger.error("Subscription notificationType is invalid");
            return;
        }
        if (subscription.getChannel() == null || subscription.getChannel().isBlank()) {
            logger.error("Subscription channel is invalid");
            return;
        }
        logger.info("Subscription {} processed successfully", subscription.getSubscriptionId());
    }

    private void triggerNotifications(GameScore gameScore) {
        logger.info("Triggering notifications for GameScore ID: {}", gameScore.getGameId());
        subscriptionCache.values().stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus()))
                .forEach(sub -> {
                    boolean notify = false;
                    if (sub.getTeam() == null || sub.getTeam().isBlank()) {
                        notify = true;
                    } else if (sub.getTeam().equalsIgnoreCase(gameScore.getHomeTeam())
                            || sub.getTeam().equalsIgnoreCase(gameScore.getAwayTeam())) {
                        notify = true;
                    }
                    if (notify) {
                        sendNotification(sub, gameScore);
                    }
                });
    }

    private void sendNotification(Subscription subscription, GameScore gameScore) {
        logger.info("Sending {} notification to user {} via {} for game {} vs {} with scores {}-{}",
                subscription.getNotificationType(),
                subscription.getUserId(),
                subscription.getChannel(),
                gameScore.getHomeTeam(),
                gameScore.getAwayTeam(),
                gameScore.getHomeScore(),
                gameScore.getAwayScore());
    }
}