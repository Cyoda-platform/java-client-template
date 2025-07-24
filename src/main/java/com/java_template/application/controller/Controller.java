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

}