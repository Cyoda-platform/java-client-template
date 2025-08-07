package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicLong laureateIdCounter = new AtomicLong(1);

    // ------------------- JOB ENDPOINTS -------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, UUID>> createJob(@RequestBody Map<String, String> request) {
        try {
            String externalId = request.get("externalId");
            if (externalId == null || externalId.isBlank()) {
                logger.error("Job creation failed: externalId is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            // Create Job object
            Job job = new Job();
            job.setExternalId(externalId);
            job.setState("SCHEDULED");
            job.setCreatedAt(OffsetDateTime.now());

            // Validate job before adding
            simulateValidationProcessorJob(job);

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();
            job.setId(technicalId);

            logger.info("Job created with technicalId {}", technicalId);

            // Trigger processing asynchronously
            new Thread(() -> processJob(technicalId, job)).start();

            Map<String, UUID> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Job creation failed: {}", e.getMessage());
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
            if (node == null || node.isEmpty()) {
                logger.error("Job with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = node.traverse().readValueAs(Job.class);
            job.setId(technicalId);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid job id format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Job with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error retrieving job {}: {}", id, ee.getCause().getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Error retrieving job {}: {}", id, e.getMessage());
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
            if (node == null || node.isEmpty()) {
                logger.error("Laureate with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = node.traverse().readValueAs(Laureate.class);
            laureate.setId(technicalId);
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid laureate id format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Laureate with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error retrieving laureate {}: {}", id, ee.getCause().getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Error retrieving laureate {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------------- SUBSCRIBER ENDPOINTS -------------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, UUID>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber.getContactEmail() == null || subscriber.getContactEmail().isBlank()) {
                logger.error("Subscriber creation failed: contactEmail is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (subscriber.getActive() == null) {
                logger.error("Subscriber creation failed: active flag is missing");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!simulateValidationProcessorSubscriber(subscriber)) {
                logger.error("Subscriber validation failed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Add subscriber to external service
            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.get();
            subscriber.setId(technicalId);

            logger.info("Subscriber created with technicalId {}", technicalId);

            // Process subscriber asynchronously
            new Thread(() -> processSubscriber(technicalId, subscriber)).start();

            Map<String, UUID> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Subscriber creation failed: {}", e.getMessage());
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
            if (node == null || node.isEmpty()) {
                logger.error("Subscriber with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = node.traverse().readValueAs(Subscriber.class);
            subscriber.setId(technicalId);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid subscriber id format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Subscriber with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error retrieving subscriber {}: {}", id, ee.getCause().getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Error retrieving subscriber {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------------- PROCESS METHODS -------------------

    private void processJob(UUID technicalId, Job job) {
        try {
            logger.info("Processing job {} - state: {}", technicalId, job.getState());

            simulateValidationProcessorJob(job);

            job.setState("INGESTING");
            // TODO: Update job state in external service - update operation not supported

            logger.info("Job {} state transitioned to INGESTING", technicalId);

            // Fetch laureates from OpenDataSoft API
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                job.setState("FAILED");
                job.setResultSummary("Failed to fetch laureates data: HTTP status " + response.getStatusCodeValue());
                job.setCompletedAt(OffsetDateTime.now());
                // TODO: Update job state in external service - update operation not supported
                logger.error("Job {} failed during data fetch", technicalId);
                return;
            }

            String body = response.getBody();
            JsonNode root = entityService.getClass().getClassLoader().loadClass("com.fasterxml.jackson.databind.ObjectMapper").getDeclaredConstructor().newInstance() instanceof com.fasterxml.jackson.databind.ObjectMapper ? new com.fasterxml.jackson.databind.ObjectMapper().readTree(body) : null;
            if (root == null) {
                job.setState("FAILED");
                job.setResultSummary("Failed to parse laureates data");
                job.setCompletedAt(OffsetDateTime.now());
                // TODO: Update job state in external service - update operation not supported
                logger.error("Job {} failed during laureates data parsing", technicalId);
                return;
            }
            JsonNode records = root.path("records");
            if (records.isMissingNode() || !records.isArray()) {
                job.setState("FAILED");
                job.setResultSummary("Invalid laureates data structure");
                job.setCompletedAt(OffsetDateTime.now());
                // TODO: Update job state in external service - update operation not supported
                logger.error("Job {} failed due to invalid laureates data structure", technicalId);
                return;
            }

            int processedCount = 0;
            List<Laureate> laureatesToAdd = new ArrayList<>();
            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                if (fields.isMissingNode()) continue;

                Laureate laureate = new Laureate();
                laureate.setLaureateId(fields.path("id").asInt(0));
                laureate.setFirstname(fields.path("firstname").asText(null));
                laureate.setSurname(fields.path("surname").asText(null));
                laureate.setGender(fields.path("gender").asText(null));
                laureate.setBorn(fields.path("born").asText(null));
                laureate.setDied(fields.path("died").isNull() ? null : fields.path("died").asText(null));
                laureate.setBorncountry(fields.path("borncountry").asText(null));
                laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
                laureate.setBorncity(fields.path("borncity").asText(null));
                laureate.setYear(fields.path("year").asText(null));
                laureate.setCategory(fields.path("category").asText(null));
                laureate.setMotivation(fields.path("motivation").asText(null));
                laureate.setAffiliationName(fields.path("name").asText(null));
                laureate.setAffiliationCity(fields.path("city").asText(null));
                laureate.setAffiliationCountry(fields.path("country").asText(null));

                laureatesToAdd.add(laureate);
                processedCount++;
            }

            if (!laureatesToAdd.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems(Laureate.ENTITY_NAME, ENTITY_VERSION, laureatesToAdd);
                List<UUID> ids = idsFuture.get();
                for (int i = 0; i < ids.size(); i++) {
                    UUID laureateId = ids.get(i);
                    Laureate laureate = laureatesToAdd.get(i);
                    laureate.setId(laureateId);
                    processLaureate(laureateId, laureate);
                }
            }

            job.setState("SUCCEEDED");
            job.setResultSummary("Ingested " + processedCount + " laureates");
            job.setCompletedAt(OffsetDateTime.now());
            // TODO: Update job state in external service - update operation not supported
            logger.info("Job {} ingestion succeeded with {} laureates", technicalId, processedCount);

            notifySubscribers(technicalId, job);

            job.setState("NOTIFIED_SUBSCRIBERS");
            // TODO: Update job state in external service - update operation not supported
            logger.info("Job {} state transitioned to NOTIFIED_SUBSCRIBERS", technicalId);

        } catch (IOException e) {
            job.setState("FAILED");
            job.setResultSummary("Exception during processing: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            // TODO: Update job state in external service - update operation not supported
            logger.error("Job {} failed with exception: {}", technicalId, e.getMessage());
        } catch (Exception e) {
            job.setState("FAILED");
            job.setResultSummary("Unexpected error: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            // TODO: Update job state in external service - update operation not supported
            logger.error("Job {} failed with unexpected error: {}", technicalId, e.getMessage());
        }
    }

    private void processLaureate(UUID technicalId, Laureate laureate) {
        logger.info("Processing laureate {}", technicalId);

        if (!simulateValidationProcessorLaureate(laureate)) {
            logger.error("Laureate {} validation failed", technicalId);
            return;
        }

        simulateEnrichmentProcessorLaureate(laureate);

        // Already persisted immutably in external service

        logger.info("Laureate {} processed successfully", technicalId);
    }

    private void processSubscriber(UUID technicalId, Subscriber subscriber) {
        logger.info("Processing subscriber {}", technicalId);
        if (!simulateValidationProcessorSubscriber(subscriber)) {
            logger.error("Subscriber {} validation failed", technicalId);
            return;
        }
        // Already persisted in external service
        logger.info("Subscriber {} processed successfully", technicalId);
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

    private void notifySubscribers(UUID jobId, Job job) {
        try {
            CompletableFuture<ArrayNode> subsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscribersArray = subsFuture.get();
            if (subscribersArray == null) return;

            for (JsonNode node : subscribersArray) {
                Subscriber subscriber = node.traverse().readValueAs(Subscriber.class);
                UUID subscriberId = UUID.fromString(node.path("technicalId").asText());
                subscriber.setId(subscriberId);
                if (Boolean.TRUE.equals(subscriber.getActive())) {
                    try {
                        if (subscriber.getWebhookUrl() != null && !subscriber.getWebhookUrl().isBlank()) {
                            sendWebhookNotification(subscriber.getWebhookUrl(), job);
                        } else {
                            sendEmailNotification(subscriber.getContactEmail(), job);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to notify subscriber {}: {}", subscriber.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to notify subscribers for job {}: {}", jobId, e.getMessage());
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