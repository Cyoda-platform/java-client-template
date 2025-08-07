package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // ----------------- JOB ---------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> request) {
        try {
            String jobName = request.get("jobName");
            if (jobName == null || jobName.isBlank()) {
                logger.error("Job creation failed: jobName is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName is required"));
            }
            Job job = new Job();
            job.setJobName(jobName);
            job.setStatus("SCHEDULED");
            job.setCreatedAt(Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalIdUuid = idFuture.get();
            String technicalId = "job-" + technicalIdUuid.toString();

            logger.info("Job created with technicalId {}", technicalId);

            // Trigger processing asynchronously
            new Thread(() -> processJob(technicalIdUuid, job)).start();

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (IllegalArgumentException e) {
            logger.error("Job creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Job creation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = extractUUID(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Job not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = parseJobFromNode(node);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving job {}: {}", technicalId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------------- SUBSCRIBER ---------------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, String> request) {
        try {
            String contactType = request.get("contactType");
            String contactValue = request.get("contactValue");
            if (contactType == null || contactType.isBlank() || contactValue == null || contactValue.isBlank()) {
                logger.error("Subscriber creation failed: contactType or contactValue missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType and contactValue are required"));
            }
            Subscriber subscriber = new Subscriber();
            subscriber.setContactType(contactType);
            subscriber.setContactValue(contactValue);
            subscriber.setSubscribedAt(Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalIdUuid = idFuture.get();
            String technicalId = "subscriber-" + technicalIdUuid.toString();

            logger.info("Subscriber created with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (IllegalArgumentException e) {
            logger.error("Subscriber creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Subscriber creation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = extractUUID(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = parseSubscriberFromNode(node);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving subscriber {}: {}", technicalId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------------- LAUREATE ---------------------

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = extractUUID(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Laureate not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = parseLaureateFromNode(node);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving laureate {}: {}", technicalId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------------- Helpers ---------------------

    private UUID extractUUID(String technicalId) {
        // technicalId format: prefix-UUID
        if (technicalId == null) throw new IllegalArgumentException("technicalId is null");
        int dashIndex = technicalId.indexOf("-");
        if (dashIndex < 0 || dashIndex + 1 >= technicalId.length()) {
            throw new IllegalArgumentException("Invalid technicalId format");
        }
        String uuidPart = technicalId.substring(dashIndex + 1);
        return UUID.fromString(uuidPart);
    }

    private Job parseJobFromNode(ObjectNode node) {
        try {
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.treeToValue(node, Job.class);
        } catch (Exception e) {
            logger.error("Failed to parse Job from node: {}", e.getMessage(), e);
            return null;
        }
    }

    private Laureate parseLaureateFromNode(ObjectNode node) {
        try {
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.treeToValue(node, Laureate.class);
        } catch (Exception e) {
            logger.error("Failed to parse Laureate from node: {}", e.getMessage(), e);
            return null;
        }
    }

    private Subscriber parseSubscriberFromNode(ObjectNode node) {
        try {
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.treeToValue(node, Subscriber.class);
        } catch (Exception e) {
            logger.error("Failed to parse Subscriber from node: {}", e.getMessage(), e);
            return null;
        }
    }
}