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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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

    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong laureateIdCounter = new AtomicLong(1);
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // ------------- JOB ENDPOINTS ----------------
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob() {
        try {
            // Generate a temporary technicalId for logging and internal usage
            String technicalId = "job-" + jobIdCounter.getAndIncrement();

            Job job = new Job();
            job.setJobId(technicalId);
            job.setStatus("SCHEDULED");
            job.setCreatedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            job.setStartedAt(null);
            job.setFinishedAt(null);
            job.setErrorMessage(null);

            // Add job via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID jobUuid = idFuture.get();

            logger.info("Job created with UUID {}", jobUuid);

            // processJob(jobUuid, job); // Removed process method call

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", jobUuid.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument during job creation", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to create job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Deserialize ObjectNode to Job
            Job job = objectMapper.treeToValue(node, Job.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID format for job id {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Invalid UUID for job id {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Execution exception getting job {}", technicalId, ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Failed to get job with id {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------- SUBSCRIBER ENDPOINTS ----------------
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            // Validate contact info
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()
                    || subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
                logger.error("Invalid subscriber contact info");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // We won't use subscriberIdCounter for technicalId, let EntityService generate UUID technicalId
            if (subscriber.getActive() == null) {
                subscriber.setActive(true);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.get();

            logger.info("Subscriber created with UUID {}", technicalId);

            // processSubscriber(technicalId, subscriber); // Removed process method call

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument during subscriber creation", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to create subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID format for subscriber id {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Invalid UUID for subscriber id {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Execution exception getting subscriber {}", technicalId, ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Failed to get subscriber with id {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<Subscriber>> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode nodes = itemsFuture.get();
            List<Subscriber> list = new ArrayList<>();
            if (nodes != null) {
                for (JsonNode node : nodes) {
                    Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                    list.add(subscriber);
                }
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            logger.error("Failed to get all subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------- LAUREATE ENDPOINTS ----------------
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID format for laureate id {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Invalid UUID for laureate id {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Execution exception getting laureate {}", technicalId, ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Failed to get laureate with id {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/laureates")
    public ResponseEntity<List<Laureate>> getLaureates(@RequestParam(required = false) String year,
                                                      @RequestParam(required = false) String category) {
        try {
            SearchConditionRequest condition = null;
            if ((year != null && !year.isBlank()) || (category != null && !category.isBlank())) {
                List<Condition> conditions = new ArrayList<>();
                if (year != null && !year.isBlank()) {
                    conditions.add(Condition.of("$.year", "EQUALS", year));
                }
                if (category != null && !category.isBlank()) {
                    conditions.add(Condition.of("$.category", "IEQUALS", category));
                }
                condition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            }

            CompletableFuture<ArrayNode> filteredItemsFuture;
            if (condition != null) {
                filteredItemsFuture = entityService.getItemsByCondition(Laureate.ENTITY_NAME, ENTITY_VERSION, condition, true);
            } else {
                filteredItemsFuture = entityService.getItems(Laureate.ENTITY_NAME, ENTITY_VERSION);
            }
            ArrayNode nodes = filteredItemsFuture.get();
            List<Laureate> result = new ArrayList<>();
            if (nodes != null) {
                for (JsonNode node : nodes) {
                    Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
                    result.add(laureate);
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to get laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------------- PROCESS METHODS REMOVED ----------------------

    private void notifySubscribers() {
        logger.info("Notifying active subscribers");
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode nodes = itemsFuture.get();
            int notifiedCount = 0;
            if (nodes != null) {
                for (JsonNode node : nodes) {
                    Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                    if (Boolean.TRUE.equals(subscriber.getActive())) {
                        logger.info("Notified subscriber {} at {}", subscriber.getSubscriberId(), subscriber.getContactAddress());
                        notifiedCount++;
                    }
                }
            }
            logger.info("Total subscribers notified: {}", notifiedCount);
        } catch (Exception e) {
            logger.error("Failed to notify subscribers", e);
        }
    }
}
