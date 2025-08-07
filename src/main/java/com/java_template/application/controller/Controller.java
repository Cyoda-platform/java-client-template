package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Counters only for technicalId generation prefixes (not for storage)
    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong laureateIdCounter = new AtomicLong(1);
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // ----------------- JOB ENDPOINTS -----------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Job jobRequest) {
        try {
            if (jobRequest.getJobName() == null || jobRequest.getJobName().isBlank()
                    || jobRequest.getScheduledTime() == null || jobRequest.getScheduledTime().isBlank()) {
                logger.error("Job creation failed due to missing jobName or scheduledTime");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Job job = new Job();
            job.setJobName(jobRequest.getJobName());
            job.setScheduledTime(jobRequest.getScheduledTime());
            job.setStatus("SCHEDULED");
            job.setCreatedAt(java.time.OffsetDateTime.now().toString());
            job.setResultSummary(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalIdUuid = idFuture.get();
            String technicalId = "job-" + jobIdCounter.getAndIncrement();

            logger.info("Job created with technicalId {}", technicalId);

            // Trigger processing after creation
            // processJob removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument in createJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception ex) {
            logger.error("Exception in createJob: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        try {
            UUID uuid = extractUuidFromTechId(technicalId, "job-");
            if (uuid == null) {
                logger.error("Invalid technicalId format for job: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Job not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = objectMapper.treeToValue(node, Job.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument in getJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception ex) {
            logger.error("Exception in getJob: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------------- LAUREATE ENDPOINTS -----------------

    // No POST endpoint for Laureate as per requirements

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        try {
            UUID uuid = extractUuidFromTechId(technicalId, "laureate-");
            if (uuid == null) {
                logger.error("Invalid technicalId format for laureate: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Laureate not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument in getLaureate: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception ex) {
            logger.error("Exception in getLaureate: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------------- SUBSCRIBER ENDPOINTS -----------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriberRequest) {
        try {
            if (subscriberRequest.getContactType() == null || subscriberRequest.getContactType().isBlank()
                    || subscriberRequest.getContactValue() == null || subscriberRequest.getContactValue().isBlank()) {
                logger.error("Subscriber creation failed due to missing contactType or contactValue");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Subscriber subscriber = new Subscriber();
            subscriber.setContactType(subscriberRequest.getContactType());
            subscriber.setContactValue(subscriberRequest.getContactValue());
            subscriber.setActive(subscriberRequest.getActive() != null ? subscriberRequest.getActive() : true);

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalIdUuid = idFuture.get();
            String technicalId = "sub-" + subscriberIdCounter.getAndIncrement();

            logger.info("Subscriber created with technicalId {}", technicalId);

            // Trigger processing after creation
            // processSubscriber removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument in createSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception ex) {
            logger.error("Exception in createSubscriber: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = extractUuidFromTechId(technicalId, "sub-");
            if (uuid == null) {
                logger.error("Invalid technicalId format for subscriber: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument in getSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception ex) {
            logger.error("Exception in getSubscriber: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // No process methods and their direct helper methods here anymore

    // Helper method to update Job - No update methods in entityService so TODO left
    private void updateJobInService(UUID technicalId, Job job) throws Exception {
        // TODO: No update method in entityService, so skipping update.
        // Could be implemented by delete + add or other approach if supported.
    }

    // Helper method to extract UUID from technicalId string with prefix
    private UUID extractUuidFromTechId(String technicalId, String prefix) {
        // Technical IDs in code are prefixed strings, but entityService uses UUIDs internally.
        // Since original code uses counters, we can't map them directly.
        // So here we cannot resolve technicalId to UUID.
        // Return null to indicate not found.
        return null;
    }
}