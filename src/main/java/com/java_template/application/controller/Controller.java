package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // --- Job Endpoints ---

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Map<String, String> request) {
        try {
            String jobName = request.get("jobName");
            if (jobName == null || jobName.isBlank()) {
                logger.error("Job creation failed: jobName is missing");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName is required"));
            }
            Job job = new Job();
            job.setJobName(jobName);
            job.setStatus("SCHEDULED");
            job.setCreatedAt(LocalDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.join();
            String techIdStr = technicalId.toString();

            logger.info("Job created with technicalId {}", techIdStr);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException ex) {
            logger.error("Job creation failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error during job creation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Job not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            Job job = objectMapper.treeToValue(node, Job.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId format {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (JsonProcessingException ex) {
            logger.error("Error processing JSON for job {}", technicalId, ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving job {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Laureate Endpoints ---

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Laureate not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId format {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (JsonProcessingException ex) {
            logger.error("Error processing JSON for laureate {}", technicalId, ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving laureate {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@Valid @RequestBody Subscriber subscriber) {
        try {
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank() ||
                subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank() ||
                subscriber.getActive() == null || !subscriber.getActive()) {
                logger.error("Subscriber creation failed: contactType or contactAddress missing or subscriber inactive");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType, contactAddress are required and subscriber must be active"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.join();
            String techIdStr = technicalId.toString();

            logger.info("Subscriber created with technicalId {}", techIdStr);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException ex) {
            logger.error("Subscriber creation failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error during subscriber creation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Subscriber not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId format {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (JsonProcessingException ex) {
            logger.error("Error processing JSON for subscriber {}", technicalId, ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving subscriber {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}