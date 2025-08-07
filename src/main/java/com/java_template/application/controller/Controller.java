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

            processJob(jobUuid, job);

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

            processSubscriber(technicalId, subscriber);

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

    // ------------------- PROCESS METHODS ----------------------

    private void processJob(UUID technicalId, Job job) {
        logger.info("Processing job {}", technicalId);

        try {
            if (!simulateJobValidation(job)) {
                job.setStatus("FAILED");
                job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
                job.setErrorMessage("Job validation failed");
                logger.error("Job validation failed for job {}", technicalId);
                // TODO: update job status in EntityService - not supported now
                return;
            }
            job.setStatus("INGESTING");
            job.setStartedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            logger.info("Job {} status set to INGESTING", technicalId);

            // TODO: update job status in EntityService - not supported now

            // Call OpenDataSoft API to ingest Nobel laureates data
            String apiUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=1000";
            org.springframework.http.ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                job.setStatus("FAILED");
                job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
                job.setErrorMessage("Failed to fetch data from OpenDataSoft API. HTTP status: " + response.getStatusCodeValue());
                logger.error("Failed to fetch data from API, status {}", response.getStatusCodeValue());
                // TODO: update job status in EntityService
                return;
            }

            String body = response.getBody();
            JsonNode root = objectMapper.readTree(body);
            JsonNode records = root.path("records");

            if (records.isMissingNode() || !records.isArray()) {
                job.setStatus("FAILED");
                job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
                job.setErrorMessage("Malformed API response: missing records array");
                logger.error("Malformed API response: missing records array");
                // TODO: update job status in EntityService
                return;
            }

            List<Laureate> laureatesToAdd = new ArrayList<>();

            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                if (fields.isMissingNode()) {
                    continue; // skip malformed record
                }
                Laureate laureate = new Laureate();

                // parse and set fields with null-safe extraction
                laureate.setId(fields.path("id").isInt() ? fields.path("id").intValue() : null);
                laureate.setFirstname(fields.path("firstname").asText(null));
                laureate.setSurname(fields.path("surname").asText(null));
                laureate.setBorn(fields.path("born").asText(null));
                laureate.setDied(fields.path("died").isNull() ? null : fields.path("died").asText(null));
                laureate.setBorncountry(fields.path("borncountry").asText(null));
                laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
                laureate.setBorncity(fields.path("borncity").asText(null));
                laureate.setGender(fields.path("gender").asText(null));
                laureate.setYear(fields.path("year").asText(null));
                laureate.setCategory(fields.path("category").asText(null));
                laureate.setMotivation(fields.path("motivation").asText(null));
                laureate.setName(fields.path("name").asText(null));
                laureate.setCity(fields.path("city").asText(null));
                laureate.setCountry(fields.path("country").asText(null));

                if (simulateLaureateValidation(laureate)) {
                    simulateLaureateEnrichment(laureate);
                    laureatesToAdd.add(laureate);
                } else {
                    logger.warn("Skipping invalid laureate record id {}", laureate.getId());
                }
            }

            if (!laureatesToAdd.isEmpty()) {
                // Add all laureates via EntityService
                CompletableFuture<List<UUID>> addItemsFuture = entityService.addItems(Laureate.ENTITY_NAME, ENTITY_VERSION, laureatesToAdd);
                List<UUID> laureateUuids = addItemsFuture.get();
                for (int i = 0; i < laureateUuids.size(); i++) {
                    UUID uuid = laureateUuids.get(i);
                    logger.info("Laureate entity created with UUID {}", uuid);
                    processLaureate(uuid, laureatesToAdd.get(i));
                }
            }

            job.setStatus("SUCCEEDED");
            job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            logger.info("Job {} ingestion succeeded", technicalId);
            // TODO: update job status in EntityService

            // Notify all active subscribers
            notifySubscribers();

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            logger.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);
            // TODO: update job status in EntityService

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            job.setErrorMessage("Exception during job processing: " + e.getMessage());
            logger.error("Exception during job processing for job {}", technicalId, e);
            // TODO: update job status in EntityService
        }
    }

    private boolean simulateJobValidation(Job job) {
        return job != null && job.getStatus() != null && job.getStatus().equals("SCHEDULED");
    }

    private void processLaureate(UUID technicalId, Laureate laureate) {
        logger.info("Processing laureate {}", technicalId);
        if (!simulateLaureateValidation(laureate)) {
            logger.error("Laureate validation failed for {}", technicalId);
            return;
        }
        simulateLaureateEnrichment(laureate);
        // Immutable persistence simulation: no update operation allowed, so do nothing
        logger.info("Laureate {} processed and saved", technicalId);
    }

    private boolean simulateLaureateValidation(Laureate laureate) {
        if (laureate == null) return false;
        if (laureate.getId() == null) return false;
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        return true;
    }

    private void simulateLaureateEnrichment(Laureate laureate) {
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private void processSubscriber(UUID technicalId, Subscriber subscriber) {
        logger.info("Processing subscriber {}", technicalId);
        if (!simulateSubscriberValidation(subscriber)) {
            logger.error("Subscriber validation failed for {}", technicalId);
            return;
        }
        // Immutable persistence simulated by addItem already done
        logger.info("Subscriber {} processed and saved", technicalId);
    }

    private boolean simulateSubscriberValidation(Subscriber subscriber) {
        if (subscriber == null) return false;
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) return false;
        return true;
    }

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