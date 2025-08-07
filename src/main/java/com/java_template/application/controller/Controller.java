package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Local counters to generate temporary keys before saving, for logging or processing usage
    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong laureateIdCounter = new AtomicLong(1);
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

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
            UUID technicalId = idFuture.get();

            logger.info("Job created with technicalId {}", technicalId);

            // Trigger processing after creation
            // processJob method removed for workflow extraction

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Job not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = objectMapper.treeToValue(node, Job.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof NoSuchElementException) {
                logger.error("Job not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("ExecutionException in getJob: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Exception in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------------- LAUREATE ENDPOINTS -----------------

    // No POST endpoint for Laureate as per requirements

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Laureate not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof NoSuchElementException) {
                logger.error("Laureate not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("ExecutionException in getLaureate: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Exception in getLaureate: {}", e.getMessage());
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
            UUID technicalId = idFuture.get();

            logger.info("Subscriber created with technicalId {}", technicalId);

            // Trigger processing after creation
            // processSubscriber method removed for workflow extraction

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof NoSuchElementException) {
                logger.error("Subscriber not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("ExecutionException in getSubscriber: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Exception in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Other methods remain intact (CRUD, endpoints, etc.)

}