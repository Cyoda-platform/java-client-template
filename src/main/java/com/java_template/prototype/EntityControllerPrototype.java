package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity as SpringResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Laureate> laureateCache = new ConcurrentHashMap<>();
    private final AtomicLong laureateIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ----------------- JOB ---------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> request) {
        String jobName = request.get("jobName");
        if (jobName == null || jobName.isBlank()) {
            log.error("Job creation failed: jobName is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName is required"));
        }
        String technicalId = "job-" + jobIdCounter.getAndIncrement();
        Job job = new Job();
        job.setJobName(jobName);
        job.setStatus("SCHEDULED");
        job.setCreatedAt(Instant.now().toString());
        jobCache.put(technicalId, job);
        log.info("Job created with technicalId {}", technicalId);

        // Trigger processing asynchronously (simulate async with new thread here)
        new Thread(() -> processJob(technicalId, job)).start();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found for id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    private void processJob(String technicalId, Job job) {
        log.info("Processing job {} with status {}", technicalId, job.getStatus());
        try {
            // Transition to INGESTING
            job.setStatus("INGESTING");
            jobCache.put(technicalId, job);
            log.info("Job {} status updated to INGESTING", technicalId);

            // Call OpenDataSoft API to fetch Nobel laureates data
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=1000";
            SpringResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed API call with status: " + response.getStatusCode());
            }
            String body = response.getBody();
            JsonNode rootNode = objectMapper.readTree(body);
            JsonNode records = rootNode.path("records");
            if (!records.isArray()) {
                throw new RuntimeException("API response does not contain records array");
            }

            int ingestedCount = 0;
            for (JsonNode recordNode : records) {
                JsonNode fields = recordNode.path("fields");
                if (fields.isMissingNode()) continue;

                // Build Laureate entity from fields
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

                // Generate technicalId for laureate (append-only, immutable)
                String laureateTechnicalId = "laureate-" + laureateIdCounter.getAndIncrement();
                laureateCache.put(laureateTechnicalId, laureate);

                // Trigger processLaureate event
                processLaureate(laureateTechnicalId, laureate);
                ingestedCount++;
            }

            job.setStatus("SUCCEEDED");
            job.setCompletedAt(Instant.now().toString());
            jobCache.put(technicalId, job);
            log.info("Job {} ingestion succeeded, {} laureates ingested", technicalId, ingestedCount);

        } catch (Exception e) {
            log.error("Job {} ingestion failed: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            jobCache.put(technicalId, job);
        }

        // Trigger job notification workflow
        processJobNotification(technicalId, job);
    }

    private void processLaureate(String technicalId, Laureate entity) {
        log.info("Processing laureate {}", technicalId);

        // Validation processor simulation
        boolean valid = runLaureateValidationProcessor(entity);
        if (!valid) {
            log.error("Laureate {} validation failed", technicalId);
            return;
        }

        // Enrichment processor simulation
        runLaureateEnrichmentProcessor(entity);

        // Mark processing complete (immutable entity, so no update)
        log.info("Laureate {} processed successfully", technicalId);
    }

    private boolean runLaureateValidationProcessor(Laureate laureate) {
        // Validate required fields are non-blank
        if (laureate.getLaureateId().isBlank()) return false;
        if (laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname().isBlank()) return false;
        if (laureate.getYear().isBlank()) return false;
        if (laureate.getCategory().isBlank()) return false;
        return true;
    }

    private void runLaureateEnrichmentProcessor(Laureate laureate) {
        // Example enrichment: normalize country code to uppercase
        if (laureate.getBornCountryCode() != null) {
            laureate.setBornCountryCode(laureate.getBornCountryCode().toUpperCase());
        }
    }

    // ----------------- SUBSCRIBER ---------------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, String> request) {
        String contactType = request.get("contactType");
        String contactValue = request.get("contactValue");
        if (contactType == null || contactType.isBlank() || contactValue == null || contactValue.isBlank()) {
            log.error("Subscriber creation failed: contactType or contactValue missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType and contactValue are required"));
        }
        String technicalId = "subscriber-" + subscriberIdCounter.getAndIncrement();
        Subscriber subscriber = new Subscriber();
        subscriber.setContactType(contactType);
        subscriber.setContactValue(contactValue);
        subscriber.setSubscribedAt(Instant.now().toString());
        subscriberCache.put(technicalId, subscriber);
        log.info("Subscriber created with technicalId {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found for id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // ----------------- LAUREATE ---------------------

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            log.error("Laureate not found for id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(laureate);
    }

    // ----------------- JOB NOTIFICATION ---------------------

    private void processJobNotification(String technicalId, Job job) {
        log.info("Processing job notification for job {}", technicalId);
        if (!"SUCCEEDED".equals(job.getStatus()) && !"FAILED".equals(job.getStatus())) {
            log.info("Job {} is not in SUCCEEDED or FAILED state, skipping notification", technicalId);
            return;
        }

        // Retrieve all subscribers
        Collection<Subscriber> subscribers = subscriberCache.values();
        for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
            Subscriber subscriber = entry.getValue();
            try {
                sendNotification(subscriber, job);
                log.info("Notification sent to subscriber {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to send notification to subscriber {}: {}", entry.getKey(), e.getMessage());
            }
        }

        // Update job status to NOTIFIED_SUBSCRIBERS
        job.setStatus("NOTIFIED_SUBSCRIBERS");
        jobCache.put(technicalId, job);
        log.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // Simple simulation: log notification content
        String message = String.format("Job %s completed with status %s", job.getJobName(), job.getStatus());
        if ("FAILED".equals(job.getStatus()) && job.getErrorMessage() != null) {
            message += ". Error: " + job.getErrorMessage();
        }
        // For email, webhook, etc. In real app, implement sending logic here.
        log.info("Sending notification to [{}]: {}", subscriber.getContactType(), message);
    }
}