package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Subscription;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/prototype/jobs")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    // ==================== JOB ENDPOINTS ====================

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createJob(@RequestBody @Valid JobCreateRequest request) {
        logger.info("Received request to create Job: {}", request);
        Job job = new Job();
        job.setName(request.getName());
        job.setDescription(request.getDescription());
        job.setTechnicalId(null); // ensure no technicalId set before add
        if (!job.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        return entityService.addItem("Job", ENTITY_VERSION, job)
                .thenApply(id -> {
                    logger.info("Job created with id: {}", id);
                    return ResponseEntity.ok(new IdResponse(id.toString(), "Job created and processed"));
                });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<Job>> getJob(@Valid @ModelAttribute JobGetRequest request) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(request.getId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job id format");
        }
        logger.info("Fetching Job with technicalId: {}", technicalId);
        return entityService.getItem("Job", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Job not found with technicalId: {}", technicalId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
                    }
                    Job job = objectNode.traverse().readValueAs(Job.class);
                    return ResponseEntity.ok(job);
                });
    }

    @PutMapping
    public CompletableFuture<ResponseEntity<?>> updateJob(@RequestBody @Valid JobUpdateRequest request) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(request.getId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job id format");
        }
        logger.info("Updating Job with technicalId: {}", technicalId);
        return entityService.getItem("Job", ENTITY_VERSION, technicalId).thenCompose(existingNode -> {
            if (existingNode == null || existingNode.isEmpty()) {
                logger.error("Job not found with technicalId: {}", technicalId);
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
            }
            Job existing = existingNode.traverse().readValueAs(Job.class);
            existing.setName(request.getName());
            existing.setDescription(request.getDescription());
            existing.setTechnicalId(technicalId); // keep original technicalId
            if (!existing.isValid()) {
                logger.error("Job validation failed for technicalId: {}", technicalId);
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job data");
            }
            return entityService.updateItem("Job", ENTITY_VERSION, technicalId, existing)
                    .thenApply(updatedId -> {
                        logger.info("Job updated with technicalId: {}", updatedId);
                        processJob(existing);
                        return ResponseEntity.ok(new IdResponse(updatedId.toString(), "Job updated and processed"));
                    });
        });
    }

    @DeleteMapping
    public CompletableFuture<ResponseEntity<?>> deleteJob(@Valid @ModelAttribute JobDeleteRequest request) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(request.getId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job id format");
        }
        logger.info("Deleting Job with technicalId: {}", technicalId);
        return entityService.deleteItem("Job", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    if (deletedId == null) {
                        logger.error("Job not found with technicalId: {}", technicalId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
                    }
                    return ResponseEntity.ok(new StatusResponse("Job deleted"));
                });
    }

    private void processJob(Job job) {
        logger.info("Processing Job event for technicalId: {}", job.getTechnicalId());
        // TODO: Replace this mock logic with actual event processing integration
    }


    // ==================== SUBSCRIPTION ENDPOINTS (LOCAL CACHE, unchanged) ====================

    // Local cache and logic for Subscription retained as is per instructions

    private final java.util.concurrent.ConcurrentHashMap<String, Subscription> subscriptionCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong subscriptionIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping(path = "/subscriptions", consumes = "application/json")
    public ResponseEntity<?> createSubscription(@RequestBody @Valid SubscriptionCreateRequest request) {
        logger.info("Received request to create Subscription: {}", request);
        Subscription sub = new Subscription();
        sub.setEmail(request.getEmail());
        sub.setFrequency(request.getFrequency());
        String id = addSubscription(sub);
        logger.info("Subscription created with id: {}", id);
        return ResponseEntity.ok(new IdResponse(id, "Subscription created and processed"));
    }

    @GetMapping(path = "/subscriptions")
    public ResponseEntity<Subscription> getSubscription(@Valid @ModelAttribute SubscriptionGetRequest request) {
        String id = request.getId();
        logger.info("Fetching Subscription with id: {}", id);
        Subscription sub = subscriptionCache.get(id);
        if (sub == null) {
            logger.error("Subscription not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscription not found");
        }
        return ResponseEntity.ok(sub);
    }

    @PutMapping(path = "/subscriptions")
    public ResponseEntity<?> updateSubscription(@RequestBody @Valid SubscriptionUpdateRequest request) {
        String id = request.getId();
        logger.info("Updating Subscription with id: {}", id);
        Subscription existing = subscriptionCache.get(id);
        if (existing == null) {
            logger.error("Subscription not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscription not found");
        }
        existing.setEmail(request.getEmail());
        existing.setFrequency(request.getFrequency());
        existing.setTechnicalId(UUID.randomUUID());
        if (!existing.isValid()) {
            logger.error("Subscription validation failed for id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Subscription data");
        }
        subscriptionCache.put(id, existing);
        processSubscription(existing);
        return ResponseEntity.ok(new IdResponse(id, "Subscription updated and processed"));
    }

    @DeleteMapping(path = "/subscriptions")
    public ResponseEntity<?> deleteSubscription(@Valid @ModelAttribute SubscriptionDeleteRequest request) {
        String id = request.getId();
        logger.info("Deleting Subscription with id: {}", id);
        Subscription removed = subscriptionCache.remove(id);
        if (removed == null) {
            logger.error("Subscription not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscription not found");
        }
        return ResponseEntity.ok(new StatusResponse("Subscription deleted"));
    }

    private String addSubscription(Subscription subscription) {
        if (subscription == null) throw new IllegalArgumentException("Subscription cannot be null");
        String id = String.valueOf(subscriptionIdCounter.getAndIncrement());
        subscription.setId(id);
        subscription.setTechnicalId(UUID.randomUUID());
        if (!subscription.isValid()) {
            throw new IllegalArgumentException("Invalid Subscription data");
        }
        subscriptionCache.put(id, subscription);
        logger.info("Subscription added to cache with id: {}", id);
        processSubscription(subscription);
        return id;
    }

    private void processSubscription(Subscription subscription) {
        logger.info("Processing Subscription event for id: {}", subscription.getId());
        // TODO: Replace this mock logic with actual event processing integration
    }


    // ==================== GAMESCORE ENDPOINTS (LOCAL CACHE, unchanged) ====================

    private final java.util.concurrent.ConcurrentHashMap<String, GameScore> gameScoreCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong gameScoreIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping(path = "/gamescores", consumes = "application/json")
    public ResponseEntity<?> createGameScore(@RequestBody @Valid GameScoreCreateRequest request) {
        logger.info("Received request to create GameScore: {}", request);
        GameScore gs = new GameScore();
        gs.setGameId(request.getGameId());
        gs.setHomeTeam(request.getHomeTeam());
        gs.setAwayTeam(request.getAwayTeam());
        gs.setHomeScore(request.getHomeScore());
        gs.setAwayScore(request.getAwayScore());
        String id = addGameScore(gs);
        logger.info("GameScore created with id: {}", id);
        return ResponseEntity.ok(new IdResponse(id, "GameScore created and processed"));
    }

    @GetMapping(path = "/gamescores")
    public ResponseEntity<GameScore> getGameScore(@Valid @ModelAttribute GameScoreGetRequest request) {
        String id = request.getId();
        logger.info("Fetching GameScore with id: {}", id);
        GameScore gs = gameScoreCache.get(id);
        if (gs == null) {
            logger.error("GameScore not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "GameScore not found");
        }
        return ResponseEntity.ok(gs);
    }

    @PutMapping(path = "/gamescores")
    public ResponseEntity<?> updateGameScore(@RequestBody @Valid GameScoreUpdateRequest request) {
        String id = request.getId();
        logger.info("Updating GameScore with id: {}", id);
        GameScore existing = gameScoreCache.get(id);
        if (existing == null) {
            logger.error("GameScore not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "GameScore not found");
        }
        existing.setGameId(request.getGameId());
        existing.setHomeTeam(request.getHomeTeam());
        existing.setAwayTeam(request.getAwayTeam());
        existing.setHomeScore(request.getHomeScore());
        existing.setAwayScore(request.getAwayScore());
        existing.setTechnicalId(UUID.randomUUID());
        if (!existing.isValid()) {
            logger.error("GameScore validation failed for id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid GameScore data");
        }
        gameScoreCache.put(id, existing);
        processGameScore(existing);
        return ResponseEntity.ok(new IdResponse(id, "GameScore updated and processed"));
    }

    @DeleteMapping(path = "/gamescores")
    public ResponseEntity<?> deleteGameScore(@Valid @ModelAttribute GameScoreDeleteRequest request) {
        String id = request.getId();
        logger.info("Deleting GameScore with id: {}", id);
        GameScore removed = gameScoreCache.remove(id);
        if (removed == null) {
            logger.error("GameScore not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "GameScore not found");
        }
        return ResponseEntity.ok(new StatusResponse("GameScore deleted"));
    }

    private String addGameScore(GameScore gameScore) {
        if (gameScore == null) throw new IllegalArgumentException("GameScore cannot be null");
        String id = String.valueOf(gameScoreIdCounter.getAndIncrement());
        gameScore.setId(id);
        gameScore.setTechnicalId(UUID.randomUUID());
        if (!gameScore.isValid()) {
            throw new IllegalArgumentException("Invalid GameScore data");
        }
        gameScoreCache.put(id, gameScore);
        logger.info("GameScore added to cache with id: {}", id);
        processGameScore(gameScore);
        return id;
    }

    private void processGameScore(GameScore gameScore) {
        logger.info("Processing GameScore event for id: {}", gameScore.getId());
        // TODO: Replace this mock logic with actual event processing integration
    }


    // ==================== REQUEST & RESPONSE DTOs ====================

    @Data
    public static class JobCreateRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 500)
        private String description;
    }

    @Data
    public static class JobGetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class JobUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 500)
        private String description;
    }

    @Data
    public static class JobDeleteRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class SubscriptionCreateRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Pattern(regexp = "daily|weekly|monthly", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String frequency;
    }

    @Data
    public static class SubscriptionGetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class SubscriptionUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Pattern(regexp = "daily|weekly|monthly", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String frequency;
    }

    @Data
    public static class SubscriptionDeleteRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class GameScoreCreateRequest {
        @NotBlank
        private String gameId;

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;

        @Min(0)
        private int homeScore;

        @Min(0)
        private int awayScore;
    }

    @Data
    public static class GameScoreGetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class GameScoreUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        private String gameId;

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;

        @Min(0)
        private int homeScore;

        @Min(0)
        private int awayScore;
    }

    @Data
    public static class GameScoreDeleteRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class IdResponse {
        private final String id;
        private final String status;
    }

    @Data
    public static class StatusResponse {
        private final String status;
    }
}