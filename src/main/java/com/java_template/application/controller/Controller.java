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

    private void processJob(UUID technicalId, Job job) {
        logger.info("Processing job {} with status {}", technicalId, job.getStatus());
        try {
            // Transition to INGESTING
            job.setStatus("INGESTING");
            entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job).get(); // todo: replace update logic properly
            logger.info("Job {} status updated to INGESTING", technicalId);

            // Call OpenDataSoft API to fetch Nobel laureates data
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=1000";
            var response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed API call with status: " + response.getStatusCode());
            }
            String body = response.getBody();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var rootNode = objectMapper.readTree(body);
            var records = rootNode.path("records");
            if (!records.isArray()) {
                throw new RuntimeException("API response does not contain records array");
            }

            int ingestedCount = 0;
            List<Laureate> laureatesToAdd = new ArrayList<>();
            for (var recordNode : records) {
                var fields = recordNode.path("fields");
                if (fields.isMissingNode()) continue;

                Laureate laureate = new Laureate();
                laureate.setLaureateId(fields.path("id").asText(""));
                laureate.setFirstname(fields.path("firstname").asText(""));
                laureate.setSurname(fields.path("surname").asText(""));
                laureate.setGender(fields.path("gender").asText(""));
                laureate.setBorn(fields.path("born").asText(""));
                laureate.setDied(fields.path("died").asText(""));
                laureate.setBornCountry(fields.path("borncountry").asText(""));
                laureate.setBornCountryCode(fields.path("borncountrycode").asText(""));
                laureate.setBornCity(fields.path("borncity").asText(""));
                laureate.setYear(fields.path("year").asText(""));
                laureate.setCategory(fields.path("category").asText(""));
                laureate.setMotivation(fields.path("motivation").asText(""));
                laureate.setAffiliationName(fields.path("name").asText(""));
                laureate.setAffiliationCity(fields.path("city").asText(""));
                laureate.setAffiliationCountry(fields.path("country").asText(""));
                laureate.setIngestedAt(Instant.now().toString());

                laureatesToAdd.add(laureate);
            }

            if (!laureatesToAdd.isEmpty()) {
                // Add all laureates at once
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems(Laureate.ENTITY_NAME, ENTITY_VERSION, laureatesToAdd);
                List<UUID> ids = idsFuture.get();
                for (int i = 0; i < ids.size(); i++) {
                    String laureateTechnicalId = "laureate-" + ids.get(i).toString();
                    Laureate laureate = laureatesToAdd.get(i);
                    processLaureate(laureateTechnicalId, laureate);
                    ingestedCount++;
                }
            }

            job.setStatus("SUCCEEDED");
            job.setCompletedAt(Instant.now().toString());
            entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job).get(); // todo: replace update logic properly
            logger.info("Job {} ingestion succeeded, {} laureates ingested", technicalId, ingestedCount);

        } catch (Exception e) {
            logger.error("Job {} ingestion failed: {}", technicalId, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            try {
                entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job).get(); // todo: replace update logic properly
            } catch (Exception ex) {
                logger.error("Failed to update job after failure: {}", ex.getMessage(), ex);
            }
        }

        // Trigger job notification workflow
        processJobNotification(technicalId, job);
    }

    private void processLaureate(String technicalId, Laureate entity) {
        logger.info("Processing laureate {}", technicalId);

        // Validation processor simulation
        boolean valid = runLaureateValidationProcessor(entity);
        if (!valid) {
            logger.error("Laureate {} validation failed", technicalId);
            return;
        }

        // Enrichment processor simulation
        runLaureateEnrichmentProcessor(entity);

        // Mark processing complete (immutable entity, so no update)
        logger.info("Laureate {} processed successfully", technicalId);
    }

    private boolean runLaureateValidationProcessor(Laureate laureate) {
        if (laureate.getLaureateId() == null || laureate.getLaureateId().isBlank()) return false;
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        return true;
    }

    private void runLaureateEnrichmentProcessor(Laureate laureate) {
        if (laureate.getBornCountryCode() != null) {
            laureate.setBornCountryCode(laureate.getBornCountryCode().toUpperCase());
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

    // ----------------- JOB NOTIFICATION ---------------------

    private void processJobNotification(UUID technicalId, Job job) {
        logger.info("Processing job notification for job {}", technicalId);
        if (!"SUCCEEDED".equals(job.getStatus()) && !"FAILED".equals(job.getStatus())) {
            logger.info("Job {} is not in SUCCEEDED or FAILED state, skipping notification", technicalId);
            return;
        }

        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscribersNodes = subscribersFuture.get();
            if (subscribersNodes == null || subscribersNodes.isEmpty()) {
                logger.info("No subscribers found for notification");
            } else {
                for (var node : subscribersNodes) {
                    Subscriber subscriber = parseSubscriberFromNode((ObjectNode) node);
                    try {
                        sendNotification(subscriber, job);
                        logger.info("Notification sent to subscriber");
                    } catch (Exception e) {
                        logger.error("Failed to send notification to subscriber: {}", e.getMessage(), e);
                    }
                }
            }

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job).get(); // todo: replace update logic properly
            logger.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);
        } catch (Exception e) {
            logger.error("Error processing job notification for job {}: {}", technicalId, e.getMessage(), e);
        }
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        String message = String.format("Job %s completed with status %s", job.getJobName(), job.getStatus());
        if ("FAILED".equals(job.getStatus()) && job.getErrorMessage() != null) {
            message += ". Error: " + job.getErrorMessage();
        }
        logger.info("Sending notification to [{}]: {}", subscriber.getContactType(), message);
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