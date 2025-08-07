package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            job.setCreatedAt(java.time.LocalDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();

            String techIdString = technicalId.toString();
            logger.info("Job created with technicalId {}", techIdString);

            // processJob removed to workflow prototype

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdString));
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal error in createJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Job not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            Job job = objectMapper.treeToValue(node, Job.class);
            return ResponseEntity.ok(job);

        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                logger.error("Bad request in getJob: {}", e.getCause().getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getCause().getMessage()));
            }
            logger.error("Internal error in getJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Internal error in getJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Laureate Endpoints ---

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Laureate not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                logger.error("Bad request in getLaureate: {}", e.getCause().getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getCause().getMessage()));
            }
            logger.error("Internal error in getLaureate: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Internal error in getLaureate: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank() ||
                    subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank() ||
                    subscriber.getActive() == null || !subscriber.getActive()) {
                logger.error("Subscriber creation failed: contactType or contactAddress missing or subscriber inactive");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType, contactAddress are required and subscriber must be active"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.get();
            String techIdString = technicalId.toString();

            // Set subscriberId to technicalId string
            subscriber.setSubscriberId(techIdString);

            logger.info("Subscriber created with technicalId {}", techIdString);

            // processSubscriber removed to workflow prototype

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdString));
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal error in createSubscriber: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Subscriber not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                logger.error("Bad request in getSubscriber: {}", e.getCause().getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getCause().getMessage()));
            }
            logger.error("Internal error in getSubscriber: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Internal error in getSubscriber: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}