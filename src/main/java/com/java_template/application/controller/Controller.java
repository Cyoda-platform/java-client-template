package com.java_template.application.controller;

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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();

    // -------- JOB Endpoints --------

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Map<String, String> request) {
        try {
            String jobName = request.get("jobName");
            if (jobName == null || jobName.isBlank()) {
                logger.error("Job creation failed: jobName is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName is required"));
            }
            Job job = new Job();
            job.setJobName(jobName);
            job.setStatus("SCHEDULED");
            job.setCreatedAt(java.time.Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();
            logger.info("Job created with technicalId: {}", technicalId);

            // processJob method removed

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in createJob: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Job not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            // Convert ObjectNode to Job object
            Job job = entityService.getObjectMapper().treeToValue(node, Job.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                logger.error("Job not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            logger.error("ExecutionException in getJob: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Exception in getJob: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // -------- LAUREATE Endpoints --------

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Laureate not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
            }
            Laureate laureate = entityService.getObjectMapper().treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                logger.error("Laureate not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
            }
            logger.error("ExecutionException in getLaureate: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Exception in getLaureate: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // -------- SUBSCRIBER Endpoints --------

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber.getSubscriberName() == null || subscriber.getSubscriberName().isBlank()) {
                logger.error("Subscriber creation failed: subscriberName is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "subscriberName is required"));
            }
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
                logger.error("Subscriber creation failed: contactType is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType is required"));
            }
            if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
                logger.error("Subscriber creation failed: contactAddress is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactAddress is required"));
            }
            if (subscriber.getSubscribedCategories() == null) {
                subscriber.setSubscribedCategories("");
            }
            if (subscriber.getActive() == null || subscriber.getActive().isBlank()) {
                subscriber.setActive("true");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.get();
            logger.info("Subscriber created with technicalId: {}", technicalId);

            // processSubscriber method removed

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in createSubscriber: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
            }
            Subscriber subscriber = entityService.getObjectMapper().treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                logger.error("Subscriber not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
            }
            logger.error("ExecutionException in getSubscriber: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Exception in getSubscriber: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // -------- SIMULATED PROCESSORS & CRITERIA --------

    private void simulateJobValidationProcessor(Job job) {
        if (job.getJobName() == null || job.getJobName().isBlank()) {
            throw new IllegalArgumentException("JobName is required");
        }
        logger.info("JobValidationProcessor passed for jobName: {}", job.getJobName());
    }

    private boolean simulateLaureateValidationProcessor(Laureate laureate) {
        if (laureate.getId() == 0) return false;
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        logger.info("LaureateValidationProcessor passed for laureate id: {}", laureate.getId());
        return true;
    }

    private void simulateLaureateEnrichmentProcessor(Laureate laureate) {
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        logger.info("LaureateEnrichmentProcessor completed for laureate id: {}", laureate.getId());
    }

    private boolean simulateSubscriberValidationProcessor(Subscriber subscriber) {
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) return false;
        logger.info("SubscriberValidationProcessor passed for subscriber: {}", subscriber.getSubscriberName());
        return true;
    }

    private void simulateSubscriberNotificationProcessor(Job job) {
        logger.info("Notifying subscribers for job: {}", job.getJobName());
        try {
            CompletableFuture<ArrayNode> laureatesFuture = entityService.getItems(Laureate.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode laureateNodes = laureatesFuture.get();
            Set<String> laureateCategories = new HashSet<>();
            for (int i = 0; i < laureateNodes.size(); i++) {
                ObjectNode node = (ObjectNode) laureateNodes.get(i);
                String category = node.has("category") && !node.get("category").isNull() ? node.get("category").asText() : null;
                if (category != null) {
                    laureateCategories.add(category);
                }
            }

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscriberNodes = subscribersFuture.get();

            for (int i = 0; i < subscriberNodes.size(); i++) {
                ObjectNode node = (ObjectNode) subscriberNodes.get(i);
                String active = node.has("active") && !node.get("active").isNull() ? node.get("active").asText() : null;
                if (!"true".equalsIgnoreCase(active)) continue;
                String subscribedCategories = node.has("subscribedCategories") && !node.get("subscribedCategories").isNull() ?
                        node.get("subscribedCategories").asText() : "";
                String[] categories = subscribedCategories.split(",");
                boolean interested = false;
                for (String cat : categories) {
                    if (laureateCategories.contains(cat.trim())) {
                        interested = true;
                        break;
                    }
                }
                if (interested) {
                    String subscriberName = node.has("subscriberName") && !node.get("subscriberName").isNull() ? node.get("subscriberName").asText() : "unknown";
                    String contactAddress = node.has("contactAddress") && !node.get("contactAddress").isNull() ? node.get("contactAddress").asText() : "unknown";
                    logger.info("Notified subscriber: {} at {}", subscriberName, contactAddress);
                }
            }
        } catch (Exception e) {
            logger.error("Exception in simulateSubscriberNotificationProcessor: ", e);
        }
    }
}