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
            processJob(technicalId, job);

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
            processSubscriber(technicalId, subscriber);

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

    // ----------------- PROCESS METHODS -----------------

    private void processJob(UUID technicalId, Job job) {
        logger.info("Processing Job {} with status {}", technicalId, job.getStatus());
        try {
            // Transition to INGESTING
            job.setStatus("INGESTING");
            // TODO: update job entity status in entityService (update not supported, skipping)
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
                // TODO: update job entity status in entityService (update not supported, skipping)
                return;
            }

            String body = response.getBody();
            JsonNode rootNode = objectMapper.readTree(body);
            JsonNode records = rootNode.path("records");
            if (!records.isArray()) {
                logger.error("API laureates data 'records' is not an array");
                job.setStatus("FAILED");
                job.setResultSummary("Invalid laureates data format");
                // TODO: update job entity status in entityService (update not supported, skipping)
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

                    CompletableFuture<UUID> laureateIdFuture = entityService.addItem(Laureate.ENTITY_NAME, ENTITY_VERSION, laureate);
                    UUID laureateTechnicalId = laureateIdFuture.get();

                    // Process Laureate
                    processLaureate(laureateTechnicalId, laureate);

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
            // TODO: update job entity status in entityService (update not supported, skipping)
            logger.info("Job {} status updated to {}", technicalId, job.getStatus());

            // Notify subscribers
            runNotifySubscribers(job);

            // Mark notified
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            // TODO: update job entity status in entityService (update not supported, skipping)
            logger.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);

        } catch (Exception ex) {
            logger.error("Exception in processJob: {}", ex.getMessage());
            job.setStatus("FAILED");
            job.setResultSummary("Exception during ingestion: " + ex.getMessage());
            // TODO: update job entity status in entityService (update not supported, skipping)
        }
    }

    private void processLaureate(UUID technicalId, Laureate laureate) {
        logger.info("Processing Laureate {}", technicalId);
        // Validation Processor
        if (!runValidateLaureate(laureate)) {
            logger.error("Validation failed for Laureate {}", technicalId);
            return;
        }
        // Enrichment Processor
        runEnrichLaureate(laureate);
        // Immutable record already saved in entityService
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
        // Example enrichment: Normalize country code to uppercase if present
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private void processSubscriber(UUID technicalId, Subscriber subscriber) {
        logger.info("Processing Subscriber {}", technicalId);
        // Validation
        if (!runValidateSubscriber(subscriber)) {
            logger.error("Validation failed for Subscriber {}", technicalId);
            return;
        }
        // Immutable record already saved in entityService
        logger.info("Subscriber {} processed successfully", technicalId);
    }

    private boolean runValidateSubscriber(Subscriber subscriber) {
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactValue() == null || subscriber.getContactValue().isBlank()) return false;
        return true;
    }

    private void runNotifySubscribers(Job job) {
        logger.info("Notifying active subscribers for Job");
        try {
            CompletableFuture<ArrayNode> subsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscribersArray = subsFuture.get();
            int notifiedCount = 0;
            for (JsonNode node : subscribersArray) {
                Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                if (subscriber.getActive() != null && subscriber.getActive()) {
                    try {
                        logger.info("Notifying subscriber at {}", subscriber.getContactValue());
                        // In real implementation, send email or webhook call here
                        notifiedCount++;
                    } catch (Exception e) {
                        logger.error("Failed to notify subscriber {}: {}", subscriber.getContactValue(), e.getMessage());
                    }
                }
            }
            logger.info("Notified {} subscribers for Job", notifiedCount);
        } catch (Exception e) {
            logger.error("Exception in runNotifySubscribers: {}", e.getMessage());
        }
    }
}