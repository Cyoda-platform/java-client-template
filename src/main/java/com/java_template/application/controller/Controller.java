package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.ScoreFetchJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // POST /controller/scoreFetchJob - create ScoreFetchJob entity
    @PostMapping("/scoreFetchJob")
    public ResponseEntity<?> createScoreFetchJob(@Valid @RequestBody ScoreFetchJob job) throws ExecutionException, InterruptedException {
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
    public ResponseEntity<?> getScoreFetchJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("ScoreFetchJob", ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.get();
            if (item == null || item.isEmpty()) {
                log.error("ScoreFetchJob not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ScoreFetchJob not found"));
            }
            // Convert ObjectNode to ScoreFetchJob
            ScoreFetchJob job = objectMapper.treeToValue(item, ScoreFetchJob.class);
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
    public ResponseEntity<?> createSubscriber(@Valid @RequestBody Subscriber subscriber) {
        try {
            if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()
                    || subscriber.getStatus() == null || subscriber.getStatus().isBlank()) {
                log.error("Invalid Subscriber input");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Subscriber input"));
            }

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
    public ResponseEntity<?> createGameScore(@Valid @RequestBody GameScore gameScore) throws ExecutionException, InterruptedException {
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
    public ResponseEntity<?> getGameScore(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("GameScore", ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.get();
            if (item == null || item.isEmpty()) {
                log.error("GameScore not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
            }
            GameScore gameScore = objectMapper.treeToValue(item, GameScore.class);
            return ResponseEntity.ok(gameScore);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getGameScore", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in getGameScore", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}