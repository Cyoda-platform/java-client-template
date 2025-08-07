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
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@NoArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicLong laureateIdCounter = new AtomicLong(1);

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // ------------------- JOB ENDPOINTS -------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, UUID>> createJob(@RequestBody Map<String, String> request) {
        try {
            String externalId = request.get("externalId");
            if (externalId == null || externalId.isBlank()) {
                logger.error("Job creation failed: externalId is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Job job = new Job();
            job.setExternalId(externalId);
            job.setState("SCHEDULED");
            job.setCreatedAt(OffsetDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();
            job.setId((long) technicalId.hashCode());

            logger.info("Job created with technicalId {}", technicalId);

            new Thread(() -> {/*processJob(technicalId, job)*/}).start();

            Map<String, UUID> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Job creation failed with illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Job creation failed with unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Job with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = objectMapper.treeToValue(node, Job.class);
            job.setId((long) technicalId.hashCode());
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Get job failed with illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Get job failed with illegal argument: {}", ee.getCause().getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Get job failed with execution error: {}", ee.getCause().getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Get job failed with unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------------- LAUREATE ENDPOINTS -------------------

    @GetMapping("/laureates/{id}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Laureate with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            laureate.setId((long) technicalId.hashCode());
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("Get laureate failed with illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Get laureate failed with illegal argument: {}", ee.getCause().getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Get laureate failed with execution error: {}", ee.getCause().getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Get laureate failed with unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------------- SUBSCRIBER ENDPOINTS -------------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, UUID>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber.getContactEmail() == null || subscriber.getContactEmail().isBlank()) {
                logger.error("Subscriber creation failed: contactEmail is missing or blank");
                return ResponseEntity.status(HttpStatus).build();
            }
            if (subscriber.getActive() == null) {
                logger.error("Subscriber creation failed: active flag is missing");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.get();
            subscriber.setId((long) technicalId.hashCode());

            logger.info("Subscriber created with technicalId {}", technicalId);

            new Thread(() -> {/*processSubscriber(technicalId, subscriber)*/}).start();

            Map<String, UUID> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Subscriber creation failed with illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Subscriber creation failed with unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            subscriber.setId((long) technicalId.hashCode());
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Get subscriber failed with illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof IllegalArgumentException) {
                logger.error("Get subscriber failed with illegal argument: {}", ee.getCause().getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Get subscriber failed with execution error: {}", ee.getCause().getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Get subscriber failed with unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------------- SIMULATE PROCESSORS AND CRITERIA -------------------

    private void simulateValidationProcessorJob(Job job) {
        if (job.getExternalId() == null || job.getExternalId().isBlank()) {
            throw new IllegalArgumentException("Job externalId must not be blank");
        }
        if (!"SCHEDULED".equals(job.getState())) {
            throw new IllegalStateException("Job state must be SCHEDULED to start processing");
        }
    }

    private boolean simulateValidationProcessorLaureate(Laureate laureate) {
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getGender() == null || laureate.getGender().isBlank()) return false;
        if (laureate.getBorn() == null || laureate.getBorn().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        return true;
    }

    private void simulateEnrichmentProcessorLaureate(Laureate laureate) {
        try {
            if (laureate.getBorn() != null && !laureate.getBorn().isBlank()) {
                LocalDate bornDate = LocalDate.parse(laureate.getBorn());
                LocalDate diedDate = null;
                if (laureate.getDied() != null && !laureate.getDied().isBlank()) {
                    diedDate = LocalDate.parse(laureate.getDied());
                }
                int age = (diedDate != null) ? Period.between(bornDate, diedDate).getYears() : Period.between(bornDate, LocalDate.now()).getYears();
                laureate.setCalculatedAge(age);
            }
        } catch (Exception e) {
            logger.error("Error calculating age for laureate {}: {}", laureate.getId(), e.getMessage());
            laureate.setCalculatedAge(null);
        }
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private boolean simulateValidationProcessorSubscriber(Subscriber subscriber) {
        if (subscriber.getContactEmail() == null || subscriber.getContactEmail().isBlank()) return false;
        if (!subscriber.getContactEmail().contains("@")) return false;
        if (subscriber.getWebhookUrl() != null && !subscriber.getWebhookUrl().isBlank()) {
            if (!subscriber.getWebhookUrl().startsWith("http://") && !subscriber.getWebhookUrl().startsWith("https://")) {
                return false;
            }
        }
        return true;
    }

    // ------------------- NOTIFICATIONS -------------------

    private void notifySubscribers(String jobId, Job job) {
        logger.info("Notifying subscribers for job {}", jobId);
        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    SearchConditionRequest.group("AND", Condition.of("$.active", "EQUALS", true)),
                    true);
            ArrayNode subscribersArray = subscribersFuture.get();
            if (subscribersArray == null) return;

            for (JsonNode subscriberNode : subscribersArray) {
                Subscriber subscriber = objectMapper.treeToValue(subscriberNode, Subscriber.class);
                UUID subId = subscriberNode.has("technicalId") ? UUID.fromString(subscriberNode.get("technicalId").asText()) : null;
                if (subId != null) subscriber.setId((long) subId.hashCode());
                try {
                    if (subscriber.getWebhookUrl() != null && !subscriber.getWebhookUrl().isBlank()) {
                        sendWebhookNotification(subscriber.getWebhookUrl(), job);
                    } else {
                        sendEmailNotification(subscriber.getContactEmail(), job);
                    }
                } catch (Exception e) {
                    logger.error("Failed to notify subscriber {}: {}", subId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Notification failed for job {}: {}", jobId, e.getMessage());
        }
    }

    private void sendEmailNotification(String email, Job job) {
        logger.info("Sending email notification to {} for job {} with state {}", email, job.getId(), job.getState());
        // Simulated email sending
    }

    private void sendWebhookNotification(String webhookUrl, Job job) {
        logger.info("Sending webhook notification to {} for job {} with state {}", webhookUrl, job.getId(), job.getState());
        // Simulated webhook sending
    }
}