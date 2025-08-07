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
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
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
@RequestMapping(path = "/entities")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);
    private final AtomicLong laureateIdCounter = new AtomicLong(1);

    public Controller(EntityService entityService) {
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ------------- JOB ENDPOINTS ----------------
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob() {
        try {
            String technicalId = "job-" + jobIdCounter.getAndIncrement();

            Job job = new Job();
            job.setJobId(technicalId);
            job.setStatus("SCHEDULED");
            job.setCreatedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            job.setStartedAt(null);
            job.setFinishedAt(null);
            job.setErrorMessage(null);

            // Persist job via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            idFuture.get(); // wait for completion

            logger.info("Job created with ID {}", technicalId);

            // processJob(technicalId, job);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to create job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        try {
            // Retrieve Job entity by technicalId
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, UUID.fromString(stripPrefix(technicalId, "job-")));
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = objectMapper.treeToValue(node, Job.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Invalid UUID format for getJob {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Execution exception in getJob", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Failed to get job {}", technicalId, e);
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
            String technicalId = "subscriber-" + subscriberIdCounter.getAndIncrement();
            subscriber.setSubscriberId(technicalId);
            if (subscriber.getActive() == null) {
                subscriber.setActive(true);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            idFuture.get();

            logger.info("Subscriber created with ID {}", technicalId);

            // processSubscriber(technicalId, subscriber);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to create subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(stripPrefix(technicalId, "subscriber-"));
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Invalid UUID format for getSubscriber {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Execution exception in getSubscriber", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Failed to get subscriber {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<Subscriber>> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode arrayNode = itemsFuture.get();
            if (arrayNode == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            List<Subscriber> list = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                list.add(subscriber);
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getAllSubscribers", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to get all subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------- LAUREATE ENDPOINTS ----------------
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(stripPrefix(technicalId, "laureate-"));
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getLaureate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Invalid UUID format for getLaureate {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Execution exception in getLaureate", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Failed to get laureate {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/laureates")
    public ResponseEntity<List<Laureate>> getLaureates(@RequestParam(required = false) String year,
                                                      @RequestParam(required = false) String category) {
        try {
            SearchConditionRequest condition;
            if ((year == null || year.isBlank()) && (category == null || category.isBlank())) {
                // No filtering, get all laureates
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Laureate.ENTITY_NAME, ENTITY_VERSION);
                ArrayNode arrayNode = itemsFuture.get();
                if (arrayNode == null) {
                    return ResponseEntity.ok(Collections.emptyList());
                }
                List<Laureate> result = new ArrayList<>();
                for (JsonNode node : arrayNode) {
                    Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
                    result.add(laureate);
                }
                return ResponseEntity.ok(result);
            } else {
                List<Condition> conditions = new ArrayList<>();
                if (year != null && !year.isBlank()) {
                    conditions.add(Condition.of("$.year", "EQUALS", year));
                }
                if (category != null && !category.isBlank()) {
                    conditions.add(Condition.of("$.category", "IEQUALS", category));
                }
                condition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Laureate.ENTITY_NAME, ENTITY_VERSION, condition, true);
                ArrayNode filteredArray = filteredItemsFuture.get();
                if (filteredArray == null) {
                    return ResponseEntity.ok(Collections.emptyList());
                }
                List<Laureate> result = new ArrayList<>();
                for (JsonNode node : filteredArray) {
                    Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
                    result.add(laureate);
                }
                return ResponseEntity.ok(result);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getLaureates", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to get laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void notifySubscribers() {
        logger.info("Notifying active subscribers");
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscribersArray = itemsFuture.get();
            int notifiedCount = 0;
            if (subscribersArray != null) {
                for (JsonNode node : subscribersArray) {
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

    // Helper method to strip prefix and parse UUID
    private String stripPrefix(String input, String prefix) {
        if (input.startsWith(prefix)) {
            return input.substring(prefix.length());
        }
        return input;
    }
}