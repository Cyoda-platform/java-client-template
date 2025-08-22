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
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String OPEN_DATA_SOFT_API = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    // POST /api/jobs - create Job, trigger ingestion
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Job job) {
        try {
            if (job == null || !job.isValid()) {
                logger.error("Invalid Job data received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            job.setStatus("PENDING");

            // Add job via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalUUID = idFuture.get();
            String technicalId = "job-" + technicalUUID.toString();

            logger.info("Job created with technicalId {}", technicalId);

            try {
                processJob(technicalId, job);
            } catch (Exception e) {
                logger.error("Error processing job {}: {}", technicalId, e.getMessage());
            }

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /api/jobs/{id} - retrieve Job by technicalId
    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable String id) {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(id.replaceFirst("job-", ""));
            } catch (Exception e) {
                logger.error("Invalid UUID format for job id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Job not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = node.traverse().readValueAs(Job.class);
            return ResponseEntity.ok(job);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST /api/subscribers - create Subscriber
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber == null || !subscriber.isValid()) {
                logger.error("Invalid Subscriber data received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalUUID = idFuture.get();
            String technicalId = "sub-" + technicalUUID.toString();

            logger.info("Subscriber created with technicalId {}", technicalId);

            try {
                processSubscriber(technicalId, subscriber);
            } catch (Exception e) {
                logger.error("Error processing subscriber {}: {}", technicalId, e.getMessage());
            }

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /api/subscribers/{id} - retrieve Subscriber
    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String id) {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(id.replaceFirst("sub-", ""));
            } catch (Exception e) {
                logger.error("Invalid UUID format for subscriber id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = node.traverse().readValueAs(Subscriber.class);
            return ResponseEntity.ok(subscriber);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /api/laureates/{id} - retrieve Laureate (no POST endpoint)
    @GetMapping("/laureates/{id}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String id) {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(id.replaceFirst("laureate-", ""));
            } catch (Exception e) {
                logger.error("Invalid UUID format for laureate id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Laureate not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = node.traverse().readValueAs(Laureate.class);
            return ResponseEntity.ok(laureate);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // processJob method - ingestion and notification workflow
    private void processJob(String technicalId, Job job) {
        logger.info("Starting processJob for {}", technicalId);
        try {
            // Update status to INGESTING and set startedAt timestamp
            job.setStatus("INGESTING");
            job.setStartedAt(java.time.Instant.now().toString());

            // TODO: Update job in EntityService - not supported, so skipped

            // Call OpenDataSoft API for Nobel laureates data
            org.springframework.http.ResponseEntity<Map> response = restTemplate.getForEntity(OPEN_DATA_SOFT_API, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch data from OpenDataSoft API");
            }

            Map body = response.getBody();
            if (body == null || !body.containsKey("records")) {
                throw new RuntimeException("Invalid data received from OpenDataSoft API");
            }

            List records = (List) body.get("records");
            if (records == null) records = Collections.emptyList();

            for (Object recordObj : records) {
                Map recordMap = (Map) recordObj;
                if (!recordMap.containsKey("record")) continue;
                Map record = (Map) recordMap.get("record");
                if (!record.containsKey("fields")) continue;
                Map fields = (Map) record.get("fields");

                Laureate laureate = new Laureate();
                // Map fields carefully with null checks and type casts
                laureate.setLaureateId(String.valueOf(fields.getOrDefault("id", UUID.randomUUID().toString())));
                laureate.setFirstname((String) fields.getOrDefault("firstname", ""));
                laureate.setSurname((String) fields.getOrDefault("surname", ""));
                laureate.setBorn((String) fields.getOrDefault("born", ""));
                laureate.setDied((String) fields.getOrDefault("died", ""));
                laureate.setBornCountry((String) fields.getOrDefault("borncountry", ""));
                laureate.setBornCity((String) fields.getOrDefault("borncity", ""));
                laureate.setGender((String) fields.getOrDefault("gender", ""));
                laureate.setYear((String) fields.getOrDefault("year", ""));
                laureate.setCategory((String) fields.getOrDefault("category", ""));
                laureate.setMotivation((String) fields.getOrDefault("motivation", ""));
                laureate.setAffiliationName((String) fields.getOrDefault("name", ""));
                laureate.setAffiliationCity((String) fields.getOrDefault("city", ""));
                laureate.setAffiliationCountry((String) fields.getOrDefault("country", ""));

                if (laureate.isValid()) {
                    CompletableFuture<UUID> addFuture = entityService.addItem(Laureate.ENTITY_NAME, ENTITY_VERSION, laureate);
                    UUID laureateUUID = addFuture.get();
                    String laureateId = "laureate-" + laureateUUID.toString();
                    processLaureate(laureateId, laureate);
                } else {
                    logger.error("Invalid laureate data skipped: {}", laureate);
                }
            }

            // Update job status to SUCCEEDED and finishedAt timestamp
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(java.time.Instant.now().toString());
            // TODO: Update job in EntityService - not supported, so skipped

            // Notify all active subscribers
            notifySubscribers();

            // Update job status to NOTIFIED
            job.setStatus("NOTIFIED");
            // TODO: Update job in EntityService - not supported, so skipped

            logger.info("processJob completed successfully for {}", technicalId);

        } catch (Exception e) {
            logger.error("processJob failed for {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setMessage(e.getMessage());
            job.setFinishedAt(java.time.Instant.now().toString());
            // TODO: Update job in EntityService - not supported, so skipped

            try {
                notifySubscribers();
                job.setStatus("NOTIFIED");
                // TODO: Update job in EntityService - not supported, so skipped
            } catch (Exception notifyEx) {
                logger.error("Failed to notify subscribers after job failure: {}", notifyEx.getMessage());
            }
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        // Validation and enrichment already done in isValid() and ingestion step
        logger.info("Processed Laureate {}", technicalId);
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        // No further processing required on creation as per requirements
        logger.info("Processed Subscriber {}", technicalId);
    }

    private void notifySubscribers() {
        logger.info("Notifying active subscribers...");
        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscribers = subscribersFuture.get();
            if (subscribers == null) return;

            for (int i = 0; i < subscribers.size(); i++) {
                ObjectNode subscriberNode = (ObjectNode) subscribers.get(i);
                Subscriber subscriber = subscriberNode.traverse().readValueAs(Subscriber.class);
                Boolean active = subscriber.getActive();
                if (Boolean.TRUE.equals(active)) {
                    String technicalId = subscriberNode.has("technicalId") ? subscriberNode.get("technicalId").asText() : "unknown";
                    logger.info("Notifying subscriber {} via {}: {}", technicalId, subscriber.getContactType(), subscriber.getContactValue());
                    // Real implementation could send email or webhook here
                }
            }
        } catch (Exception e) {
            logger.error("Error notifying subscribers: {}", e.getMessage());
        }
    }
}