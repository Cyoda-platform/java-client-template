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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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

    // --- Job Endpoints ---

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> request) {
        try {
            String scheduledAt = request.get("scheduledAt");
            if (scheduledAt == null || scheduledAt.isBlank()) {
                log.error("ScheduledAt is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "scheduledAt is required"));
            }
            String technicalId = "job-" + jobIdCounter.getAndIncrement();
            Job job = new Job();
            job.setJobId(technicalId);
            job.setStatus("SCHEDULED");
            job.setScheduledAt(scheduledAt);
            jobCache.put(technicalId, job);

            log.info("Created Job with technicalId {}", technicalId);

            processJob(technicalId, job);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error creating Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    // --- Laureate Endpoints ---

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            log.error("Laureate not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
        }
        return ResponseEntity.ok(laureate);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()
                    || subscriber.getContactValue() == null || subscriber.getContactValue().isBlank()) {
                log.error("Invalid subscriber data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType and contactValue required"));
            }
            String technicalId = "subscriber-" + subscriberIdCounter.getAndIncrement();
            subscriber.setSubscriberId(technicalId);
            subscriberCache.put(technicalId, subscriber);

            log.info("Created Subscriber with technicalId {}", technicalId);

            processSubscriber(technicalId, subscriber);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error creating Subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        }
        return ResponseEntity.ok(subscriber);
    }

    // --- Processing Methods ---

    private void processJob(String technicalId, Job job) {
        log.info("Processing Job {}", technicalId);
        // Simulate validation processor
        if (!simulateJobValidationProcessor(job)) {
            job.setStatus("FAILED");
            job.setFinishedAt(java.time.Instant.now().toString());
            job.setResultSummary("Validation failed");
            log.error("Job {} validation failed", technicalId);
            jobCache.put(technicalId, job);
            return;
        }
        job.setStatus("INGESTING");
        job.setStartedAt(java.time.Instant.now().toString());
        jobCache.put(technicalId, job);
        log.info("Job {} status updated to INGESTING", technicalId);

        try {
            // Fetch Nobel laureates data from OpenDataSoft API
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=1000";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !response.containsKey("records")) {
                throw new Exception("Invalid API response");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) response.get("records");

            int ingestedCount = 0;
            for (Map<String, Object> record : records) {
                Map<String, Object> fields = (Map<String, Object>) record.get("fields");
                if (fields == null) continue;

                Laureate laureate = mapFieldsToLaureate(fields);
                if (laureate == null) continue;

                String laureateId = "laureate-" + laureateIdCounter.getAndIncrement();
                laureate.setLaureateId(laureateId);
                laureateCache.put(laureateId, laureate);

                processLaureate(laureateId, laureate);
                ingestedCount++;
            }

            job.setStatus("SUCCEEDED");
            job.setFinishedAt(java.time.Instant.now().toString());
            job.setResultSummary("Ingested " + ingestedCount + " laureates successfully.");

            jobCache.put(technicalId, job);
            log.info("Job {} ingestion succeeded with {} laureates", technicalId, ingestedCount);

            // Notify subscribers
            notifySubscribers(technicalId, job);

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            jobCache.put(technicalId, job);
            log.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setFinishedAt(java.time.Instant.now().toString());
            job.setResultSummary("Ingestion failed: " + e.getMessage());
            jobCache.put(technicalId, job);
            log.error("Job {} ingestion failed", technicalId, e);
        }
    }

    private boolean simulateJobValidationProcessor(Job job) {
        // Validate job parameters and API endpoint accessibility
        if (job.getScheduledAt() == null || job.getScheduledAt().isBlank()) {
            log.error("Job scheduledAt is null or blank");
            return false;
        }
        // Example: could check API accessibility here (omitted)
        return true;
    }

    private Laureate mapFieldsToLaureate(Map<String, Object> fields) {
        try {
            Laureate laureate = new Laureate();
            Object idObj = fields.get("id");
            if (idObj != null) laureate.setLaureateId(idObj.toString());
            laureate.setFirstname((String) fields.getOrDefault("firstname", ""));
            laureate.setSurname((String) fields.getOrDefault("surname", ""));
            laureate.setBorn((String) fields.getOrDefault("born", ""));
            Object diedObj = fields.get("died");
            laureate.setDied(diedObj == null ? null : diedObj.toString());
            laureate.setBorncountry((String) fields.getOrDefault("borncountry", ""));
            laureate.setBorncountrycode((String) fields.getOrDefault("borncountrycode", ""));
            laureate.setBorncity((String) fields.getOrDefault("borncity", ""));
            laureate.setGender((String) fields.getOrDefault("gender", ""));
            laureate.setYear((String) fields.getOrDefault("year", ""));
            laureate.setCategory((String) fields.getOrDefault("category", ""));
            laureate.setMotivation((String) fields.getOrDefault("motivation", ""));
            laureate.setAffiliationName((String) fields.getOrDefault("name", ""));
            laureate.setAffiliationCity((String) fields.getOrDefault("city", ""));
            laureate.setAffiliationCountry((String) fields.getOrDefault("country", ""));
            return laureate;
        } catch (Exception e) {
            log.error("Error mapping fields to Laureate", e);
            return null;
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        log.info("Processing Laureate {}", technicalId);
        simulateLaureateValidationProcessor(laureate);
        simulateLaureateEnrichmentProcessor(laureate);
        // Persist immutable laureate entity in cache already done before this call
    }

    private void simulateLaureateValidationProcessor(Laureate laureate) {
        // Validate mandatory fields and formats
        if (!laureate.isValid()) {
            log.error("Laureate validation failed for {}", laureate.getLaureateId());
        } else {
            log.info("Laureate validation passed for {}", laureate.getLaureateId());
        }
    }

    private void simulateLaureateEnrichmentProcessor(Laureate laureate) {
        // Example enrichment: Normalize country codes to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber {}", technicalId);
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()
                || subscriber.getContactValue() == null || subscriber.getContactValue().isBlank()) {
            log.error("Subscriber validation failed for {}", technicalId);
            return;
        }
        if (subscriber.getActive() == null) {
            subscriber.setActive(true);
        }
        // Persistence done by cache put
        log.info("Subscriber {} processed successfully", technicalId);
    }

    private void notifySubscribers(String jobId, Job job) {
        // Notify all active subscribers
        for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
            Subscriber subscriber = entry.getValue();
            if (subscriber.getActive() != null && subscriber.getActive()) {
                sendNotification(subscriber, job);
            }
        }
        log.info("All active subscribers notified for job {}", jobId);
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // Simulate sending notification, e.g. log output or real email/webhook call
        log.info("Notifying subscriber {} via {} with value {} about job {} status {}",
                subscriber.getSubscriberId(), subscriber.getContactType(),
                subscriber.getContactValue(), job.getJobId(), job.getStatus());
    }
}