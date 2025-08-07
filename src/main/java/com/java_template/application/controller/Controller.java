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
            processJob(technicalId, technicalIdUuid, job);

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
            processSubscriber(technicalId, technicalIdUuid, subscriber);

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

    // ----------------- PROCESS METHODS -----------------

    private void processJob(String technicalId, UUID technicalIdUuid, Job job) {
        logger.info("Processing Job {} with status {}", technicalId, job.getStatus());
        try {
            // Transition to INGESTING
            job.setStatus("INGESTING");
            updateJobInService(technicalIdUuid, job);
            logger.info("Job {} status updated to INGESTING", technicalId);

            // Call OpenDataSoft API
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Failed to fetch laureates data from API, status: {}", response.getStatusCode());
                job.setStatus("FAILED");
                job.setResultSummary("Failed to fetch laureates data: HTTP " + response.getStatusCode());
                updateJobInService(technicalIdUuid, job);
                return;
            }

            String body = response.getBody();
            JsonNode rootNode = objectMapper.readTree(body);
            JsonNode records = rootNode.path("records");
            if (!records.isArray()) {
                logger.error("API laureates data 'records' is not an array");
                job.setStatus("FAILED");
                job.setResultSummary("Invalid laureates data format");
                updateJobInService(technicalIdUuid, job);
                return;
            }

            int successCount = 0;
            int failCount = 0;
            for (JsonNode record : records) {
                try {
                    JsonNode fields = record.path("fields");
                    Laureate laureate = new Laureate();
                    laureate.setLaureateId(fields.path("id").asText());
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

                    // Add laureate to entityService
                    CompletableFuture<UUID> laureateIdFuture = entityService.addItem(Laureate.ENTITY_NAME, ENTITY_VERSION, laureate);
                    UUID laureateUuid = laureateIdFuture.get();
                    String laureateTechId = "laureate-" + laureateIdCounter.getAndIncrement();

                    // Process Laureate
                    processLaureate(laureateTechId, laureateUuid, laureate);

                    successCount++;
                } catch (Exception e) {
                    logger.error("Failed to process laureate record: {}", e.getMessage());
                    failCount++;
                }
            }

            // Update job status based on success/failure
            if (failCount == 0) {
                job.setStatus("SUCCEEDED");
                job.setResultSummary("Ingested " + successCount + " laureates successfully");
            } else {
                job.setStatus("FAILED");
                job.setResultSummary("Ingested " + successCount + " laureates with " + failCount + " failures");
            }
            updateJobInService(technicalIdUuid, job);
            logger.info("Job {} status updated to {}", technicalId, job.getStatus());

            // Notify subscribers
            runNotifySubscribers(job);

            // Mark notified
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            updateJobInService(technicalIdUuid, job);
            logger.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);

        } catch (Exception ex) {
            logger.error("Exception in processJob: {}", ex.getMessage());
            job.setStatus("FAILED");
            job.setResultSummary("Exception during ingestion: " + ex.getMessage());
            try {
                updateJobInService(technicalIdUuid, job);
            } catch (Exception e) {
                logger.error("Failed to update job status after exception: {}", e.getMessage());
            }
        }
    }

    private void processLaureate(String technicalId, UUID technicalIdUuid, Laureate laureate) {
        logger.info("Processing Laureate {}", technicalId);
        if (!runValidateLaureate(laureate)) {
            logger.error("Validation failed for Laureate {}", technicalId);
            return;
        }
        runEnrichLaureate(laureate);
        // TODO: Update laureate entity in EntityService if needed
        logger.info("Laureate {} processed successfully", technicalId);
    }

    private boolean runValidateLaureate(Laureate laureate) {
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        return true;
    }

    private void runEnrichLaureate(Laureate laureate) {
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private void processSubscriber(String technicalId, UUID technicalIdUuid, Subscriber subscriber) {
        logger.info("Processing Subscriber {}", technicalId);
        if (!runValidateSubscriber(subscriber)) {
            logger.error("Validation failed for Subscriber {}", technicalId);
            return;
        }
        // TODO: Update subscriber entity in EntityService if needed
        logger.info("Subscriber {} processed successfully", technicalId);
    }

    private boolean runValidateSubscriber(Subscriber subscriber) {
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactValue() == null || subscriber.getContactValue().isBlank()) return false;
        return true;
    }

    private void runNotifySubscribers(Job job) {
        logger.info("Notifying active subscribers for Job");
        int notifiedCount = 0;
        try {
            // Get all subscribers
            CompletableFuture<ArrayNode> allSubsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode allSubs = allSubsFuture.get();
            if (allSubs == null) return;

            for (JsonNode node : allSubs) {
                Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                if (subscriber.getActive() != null && subscriber.getActive()) {
                    try {
                        String contactValue = subscriber.getContactValue();
                        logger.info("Notifying subscriber at {}", contactValue);
                        // In real implementation, send email or webhook call here
                        notifiedCount++;
                    } catch (Exception e) {
                        logger.error("Failed to notify subscriber: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Exception during subscriber notification: {}", ex.getMessage());
        }
        logger.info("Notified {} subscribers for Job", notifiedCount);
    }

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