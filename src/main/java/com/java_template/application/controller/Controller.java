package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.NbaScoreFetchJob;
import com.java_template.application.entity.Subscription;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Keep subscription cache locally as minor entity (per instruction)
    private final Map<String, Subscription> subscriptionCache = new HashMap<>();
    private long subscriptionIdCounter = 1L;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // -------------------- NbaScoreFetchJob Endpoints --------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createNbaScoreFetchJob(@RequestBody Map<String, String> request) throws JsonProcessingException, ExecutionException, InterruptedException {
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

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createNbaScoreFetchJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error creating NbaScoreFetchJob: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error creating NbaScoreFetchJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getNbaScoreFetchJob(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("NbaScoreFetchJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("NbaScoreFetchJob not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            NbaScoreFetchJob job = objectMapper.treeToValue(node, NbaScoreFetchJob.class);
            job.setJobId(id);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for NbaScoreFetchJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid id format"));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error fetching NbaScoreFetchJob {}: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching NbaScoreFetchJob {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // -------------------- GameScore Endpoints --------------------

    @GetMapping("/games/{id}")
    public ResponseEntity<?> getGameScore(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("GameScore", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("GameScore not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
            }
            GameScore gameScore = objectMapper.treeToValue(node, GameScore.class);
            gameScore.setGameId(id);
            return ResponseEntity.ok(gameScore);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for GameScore id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid id format"));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error fetching GameScore {}: {}", id, e.getMessage());
            throw e;
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
}