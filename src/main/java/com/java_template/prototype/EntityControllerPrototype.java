package com.java_template.prototype;

import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Subscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Validated
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Subscription> subscriptionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GameScore> gameScoreCache = new ConcurrentHashMap<>();

    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong subscriptionIdCounter = new AtomicLong(1);
    private final AtomicLong gameScoreIdCounter = new AtomicLong(1);

    // ==================== JOB ENDPOINTS ====================

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody @Valid JobCreateRequest request) {
        logger.info("Received request to create Job: {}", request);
        try {
            Job job = new Job();
            job.setName(request.getName());
            job.setDescription(request.getDescription());
            String id = addJob(job);
            logger.info("Job created with id: {}", id);
            return ResponseEntity.ok().body(new IdResponse(id, "Job created and processed"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Job data: {}", e.getMessage());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create Job");
        }
    }

    @GetMapping("/jobs")
    // Using @ModelAttribute for GET parameters validation workaround
    public ResponseEntity<Job> getJob(@Valid @ModelAttribute JobGetRequest request) {
        String id = request.getId();
        logger.info("Fetching Job with id: {}", id);
        Job job = jobCache.get(id);
        if (job == null) {
            logger.error("Job not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(job);
    }

    @PutMapping("/jobs")
    public ResponseEntity<?> updateJob(@RequestBody @Valid JobUpdateRequest request) {
        String id = request.getId();
        logger.info("Updating Job with id: {}", id);
        Job existing = jobCache.get(id);
        if (existing == null) {
            logger.error("Job not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setTechnicalId(UUID.randomUUID());
        if (!existing.isValid()) {
            logger.error("Job validation failed for id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        jobCache.put(id, existing);
        processJob(existing);
        return ResponseEntity.ok(new IdResponse(id, "Job updated and processed"));
    }

    @DeleteMapping("/jobs")
    public ResponseEntity<?> deleteJob(@Valid @ModelAttribute JobDeleteRequest request) {
        String id = request.getId();
        logger.info("Deleting Job with id: {}", id);
        Job removed = jobCache.remove(id);
        if (removed == null) {
            logger.error("Job not found with id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(new StatusResponse("Job deleted"));
    }

    // ==================== SUBSCRIPTION ENDPOINTS ====================

    @PostMapping("/subscriptions")
    public ResponseEntity<?> createSubscription(@RequestBody @Valid SubscriptionCreateRequest request) {
        logger.info("Received request to create Subscription: {}", request);
        try {
            Subscription sub = new Subscription();
            sub.setEmail(request.getEmail());
            sub.setFrequency(request.getFrequency());
            String id = addSubscription(sub);
            logger.info("Subscription created with id: {}", id);
            return ResponseEntity.ok(new IdResponse(id, "Subscription created and processed"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Subscription data: {}", e.getMessage());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating Subscription", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create Subscription");
        }
    }

    @GetMapping("/subscriptions")
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

    @PutMapping("/subscriptions")
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

    @DeleteMapping("/subscriptions")
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

    // ==================== GAMESCORE ENDPOINTS ====================

    @PostMapping("/gamescores")
    public ResponseEntity<?> createGameScore(@RequestBody @Valid GameScoreCreateRequest request) {
        logger.info("Received request to create GameScore: {}", request);
        try {
            GameScore gs = new GameScore();
            gs.setGameId(request.getGameId());
            gs.setHomeTeam(request.getHomeTeam());
            gs.setAwayTeam(request.getAwayTeam());
            gs.setHomeScore(request.getHomeScore());
            gs.setAwayScore(request.getAwayScore());
            String id = addGameScore(gs);
            logger.info("GameScore created with id: {}", id);
            return ResponseEntity.ok(new IdResponse(id, "GameScore created and processed"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid GameScore data: {}", e.getMessage());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating GameScore", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create GameScore");
        }
    }

    @GetMapping("/gamescores")
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

    @PutMapping("/gamescores")
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

    @DeleteMapping("/gamescores")
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

    // ==================== ENTITY CACHE MANAGEMENT & EVENT PROCESSING ====================

    private String addJob(Job job) {
        if (job == null) throw new IllegalArgumentException("Job cannot be null");
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        if (!job.isValid()) {
            throw new IllegalArgumentException("Invalid Job data");
        }
        jobCache.put(id, job);
        logger.info("Job added to cache with id: {}", id);
        processJob(job);
        return id;
    }

    private void processJob(Job job) {
        logger.info("Processing Job event for id: {}", job.getId());
        // TODO: Replace this mock logic with actual event processing integration
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