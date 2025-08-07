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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    // --- Job Endpoints ---

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Map<String, String> request) {
        try {
            String jobName = request.get("jobName");
            if (jobName == null || jobName.isBlank()) {
                logger.error("Job creation failed: jobName is missing");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName is required"));
            }
            Job job = new Job();
            job.setJobName(jobName);
            job.setStatus("SCHEDULED");
            job.setCreatedAt(LocalDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.join();
            String techIdStr = technicalId.toString();

            logger.info("Job created with technicalId {}", techIdStr);

            processJob(techIdStr, job);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException ex) {
            logger.error("Job creation failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error during job creation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Job not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId format {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving job {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Laureate Endpoints ---

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Laureate not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId format {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving laureate {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank() ||
                subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank() ||
                subscriber.getActive() == null || !subscriber.getActive()) {
                logger.error("Subscriber creation failed: contactType or contactAddress missing or subscriber inactive");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType, contactAddress are required and subscriber must be active"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.join();
            String techIdStr = technicalId.toString();

            logger.info("Subscriber created with technicalId {}", techIdStr);

            processSubscriber(techIdStr, subscriber);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException ex) {
            logger.error("Subscriber creation failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error during subscriber creation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Subscriber not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId format {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving subscriber {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Processing Methods ---

    private void processJob(String technicalId, Job job) {
        try {
            simulateValidateJob(job);
            job.setStatus("INGESTING");
            logger.info("Job {} status updated to INGESTING", technicalId);

            boolean ingestionSuccess = ingestNobelData(job);

            if (ingestionSuccess) {
                job.setStatus("SUCCEEDED");
                logger.info("Job {} ingestion succeeded", technicalId);
            } else {
                job.setStatus("FAILED");
                logger.error("Job {} ingestion failed", technicalId);
            }
            job.setCompletedAt(LocalDateTime.now());

            notifySubscribers(job);

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            logger.info("Job {} notifications sent to subscribers", technicalId);

            // TODO: consider updating job entity status in EntityService if update supported
        } catch (Exception e) {
            logger.error("Error processing job {}: {}", technicalId, e.getMessage(), e);
        }
    }

    private void simulateValidateJob(Job job) {
        if (job.getJobName() == null || job.getJobName().isBlank()) {
            logger.error("Job validation failed: jobName is blank");
            throw new IllegalArgumentException("jobName must be provided");
        }
        logger.info("Job validation succeeded for jobName {}", job.getJobName());
    }

    private boolean ingestNobelData(Job job) {
        try {
            String url = "https://public.opendatasoft.com/api/records/1.0/search/?dataset=laureates&q=&rows=100";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Failed to fetch Nobel laureates data, status code: {}", response.getStatusCode());
                return false;
            }

            String json = response.getBody();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var rootNode = objectMapper.readTree(json);
            var records = rootNode.path("records");

            if (!records.isArray()) {
                logger.error("No records array found in Nobel laureates data");
                return false;
            }

            for (var recordNode : records) {
                var fields = recordNode.path("fields");
                Laureate laureate = new Laureate();

                // We no longer set laureateId locally as id is handled by entityService
                laureate.setFirstname(getTextValue(fields, "firstname"));
                laureate.setSurname(getTextValue(fields, "surname"));
                laureate.setGender(getTextValue(fields, "gender"));
                laureate.setBorn(parseDate(getTextValue(fields, "born")));
                laureate.setDied(parseDate(getTextValue(fields, "died")));
                laureate.setBorncountry(getTextValue(fields, "borncountry"));
                laureate.setBorncountrycode(getTextValue(fields, "borncountrycode"));
                laureate.setBorncity(getTextValue(fields, "borncity"));
                laureate.setYear(getTextValue(fields, "year"));
                laureate.setCategory(getTextValue(fields, "category"));
                laureate.setMotivation(getTextValue(fields, "motivation"));
                laureate.setAffiliationName(getTextValue(fields, "affiliationname"));
                laureate.setAffiliationCity(getTextValue(fields, "affiliationcity"));
                laureate.setAffiliationCountry(getTextValue(fields, "affiliationcountry"));

                CompletableFuture<UUID> idFuture = entityService.addItem(Laureate.ENTITY_NAME, ENTITY_VERSION, laureate);
                UUID techId = idFuture.join();
                String techIdStr = techId.toString();

                processLaureate(techIdStr, laureate);
            }
            job.setResultDetails("Ingested " + records.size() + " laureates from external datasource.");
            return true;
        } catch (Exception e) {
            logger.error("Exception during ingestion of Nobel laureates: {}", e.getMessage(), e);
            return false;
        }
    }

    private String getTextValue(com.fasterxml.jackson.databind.JsonNode node, String fieldName) {
        var valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        return valueNode.asText();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            logger.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private void notifySubscribers(Job job) {
        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    SearchConditionRequest.group("AND", Condition.of("$.active", "EQUALS", true)),
                    true);
            ArrayNode subscribers = subscribersFuture.join();

            long notifiedCount = 0;
            if (subscribers != null) {
                notifiedCount = subscribers.size();
            }
            logger.info("Notified {} active subscribers for job {}", notifiedCount, job.getJobName());
        } catch (Exception e) {
            logger.error("Error notifying subscribers: {}", e.getMessage(), e);
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        try {
            simulateValidateLaureate(laureate);
            simulateEnrichLaureate(laureate);
            logger.info("Processed laureate {}", technicalId);
        } catch (Exception e) {
            logger.error("Error processing laureate {}: {}", technicalId, e.getMessage(), e);
        }
    }

    private void simulateValidateLaureate(Laureate laureate) {
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()
                || laureate.getSurname() == null || laureate.getSurname().isBlank()
                || laureate.getYear() == null || laureate.getYear().isBlank()
                || laureate.getCategory() == null || laureate.getCategory().isBlank()) {
            logger.error("Laureate validation failed: required fields missing");
            throw new IllegalArgumentException("Required laureate fields missing");
        }
        logger.info("Laureate validation succeeded");
    }

    private void simulateEnrichLaureate(Laureate laureate) {
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        logger.info("Laureate enrichment completed");
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        try {
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank() ||
                    subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
                logger.error("Subscriber validation failed for technicalId {}", technicalId);
                throw new IllegalArgumentException("Subscriber contactType and contactAddress are required");
            }
            logger.info("Processed subscriber {}", technicalId);
        } catch (Exception e) {
            logger.error("Error processing subscriber {}: {}", technicalId, e.getMessage(), e);
        }
    }
}