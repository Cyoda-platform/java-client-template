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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for entities
    private final ConcurrentHashMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Laureate> laureateCache = new ConcurrentHashMap<>();
    private final AtomicLong laureateIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // ========== JOB Endpoints ==========

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Job jobRequest) {
        try {
            // Validate mandatory fields
            if (jobRequest.getJobName() == null || jobRequest.getJobName().isBlank() ||
                jobRequest.getScheduledTime() == null || jobRequest.getScheduledTime().isBlank()) {
                log.error("Invalid job creation request: missing jobName or scheduledTime");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName and scheduledTime are required"));
            }

            // Create technical ID
            String technicalId = "job-" + jobIdCounter.getAndIncrement();

            // Set additional fields
            jobRequest.setStatus("SCHEDULED");
            jobRequest.setCreatedAt(java.time.OffsetDateTime.now().toString());
            jobRequest.setResultSummary("");

            jobCache.put(technicalId, jobRequest);

            log.info("Job created with technicalId={}", technicalId);

            // Process job asynchronously (simulate by new thread)
            new Thread(() -> processJob(technicalId, jobRequest)).start();

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error creating Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Object> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found for technicalId={}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    // ========== SUBSCRIBER Endpoints ==========

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriberRequest) {
        try {
            if (subscriberRequest.getContactType() == null || subscriberRequest.getContactType().isBlank() ||
                subscriberRequest.getContactValue() == null || subscriberRequest.getContactValue().isBlank() ||
                subscriberRequest.getActive() == null) {
                log.error("Invalid subscriber creation request: missing contactType, contactValue or active");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType, contactValue and active are required"));
            }

            String technicalId = "sub-" + subscriberIdCounter.getAndIncrement();

            subscriberRequest.setSubscriberId(technicalId);
            subscriberCache.put(technicalId, subscriberRequest);

            log.info("Subscriber created with technicalId={}", technicalId);

            processSubscriber(technicalId, subscriberRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error creating Subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Object> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found for technicalId={}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        }
        return ResponseEntity.ok(subscriber);
    }

    // ========== LAUREATE Endpoints ==========

    // No POST endpoint for laureate as per requirements

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Object> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            log.error("Laureate not found for technicalId={}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
        }
        return ResponseEntity.ok(laureate);
    }

    // ========== Processing Methods ==========

    // Process Job - ingestion, processing laureates, and notification
    private void processJob(String technicalId, Job job) {
        log.info("Processing Job {}: status={}", technicalId, job.getStatus());

        try {
            // Transition to INGESTING
            job.setStatus("INGESTING");
            jobCache.put(technicalId, job);
            log.info("Job {} status updated to INGESTING", technicalId);

            // Call OpenDataSoft API to fetch laureates data
            String apiUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=1000&offset=0";
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);

            Map<String, Object> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, Map.class).getBody();

            if (response == null || !response.containsKey("records")) {
                log.error("No records found in API response");
                job.setStatus("FAILED");
                job.setResultSummary("Failed to fetch laureates data from API");
                jobCache.put(technicalId, job);
                return;
            }

            List<?> records = (List<?>) response.get("records");
            int successCount = 0;
            int failureCount = 0;

            for (Object recordObj : records) {
                try {
                    Map<?, ?> record = (Map<?, ?>) recordObj;
                    Map<?, ?> fields = (Map<?, ?>) record.get("fields");

                    if (fields == null) {
                        failureCount++;
                        continue;
                    }

                    Laureate laureate = new Laureate();

                    // Map fields from API to laureate entity
                    // Handle nulls safely
                    Object idObj = record.get("recordid");
                    if (idObj != null) {
                        laureate.setLaureateId(idObj.toString());
                    } else if (fields.get("id") != null) {
                        laureate.setLaureateId(fields.get("id").toString());
                    } else {
                        laureate.setLaureateId("unknown-" + laureateIdCounter.getAndIncrement());
                    }

                    laureate.setFirstname((String) fields.getOrDefault("firstname", ""));
                    laureate.setSurname((String) fields.getOrDefault("surname", ""));
                    laureate.setGender((String) fields.getOrDefault("gender", ""));
                    laureate.setBorn((String) fields.getOrDefault("born", ""));
                    laureate.setDied((String) fields.getOrDefault("died", null));
                    laureate.setBorncountry((String) fields.getOrDefault("borncountry", ""));
                    laureate.setBorncountrycode((String) fields.getOrDefault("borncountrycode", ""));
                    laureate.setBorncity((String) fields.getOrDefault("borncity", ""));
                    laureate.setYear((String) fields.getOrDefault("year", ""));
                    laureate.setCategory((String) fields.getOrDefault("category", ""));
                    laureate.setMotivation((String) fields.getOrDefault("motivation", ""));
                    laureate.setAffiliationName((String) fields.getOrDefault("name", ""));
                    laureate.setAffiliationCity((String) fields.getOrDefault("city", ""));
                    laureate.setAffiliationCountry((String) fields.getOrDefault("country", ""));

                    // Generate technicalId for laureate
                    String laureateTechnicalId = "laureate-" + laureateIdCounter.getAndIncrement();

                    // Save laureate to cache
                    laureateCache.put(laureateTechnicalId, laureate);

                    // Process laureate
                    processLaureate(laureateTechnicalId, laureate);

                    successCount++;
                } catch (Exception e) {
                    log.error("Error processing laureate record", e);
                    failureCount++;
                }
            }

            // Update job status depending on success or failure
            if (failureCount == 0) {
                job.setStatus("SUCCEEDED");
                job.setResultSummary("Ingested " + successCount + " laureates successfully");
            } else {
                job.setStatus("FAILED");
                job.setResultSummary("Ingested " + successCount + " laureates with " + failureCount + " failures");
            }
            jobCache.put(technicalId, job);

            // Notify all active subscribers
            notifySubscribers(technicalId, job);

            // Update job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            jobCache.put(technicalId, job);

            log.info("Job {} processing completed with status {}", technicalId, job.getStatus());

        } catch (Exception e) {
            log.error("Error processing Job {}", technicalId, e);
            job.setStatus("FAILED");
            job.setResultSummary("Exception during processing: " + e.getMessage());
            jobCache.put(technicalId, job);
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        log.info("Processing Laureate {}", technicalId);

        // Validation processor - ensure required fields
        if (!laureate.isValid()) {
            log.error("Laureate validation failed for {}", technicalId);
            return;
        }

        // Enrichment processor - normalize country code to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }

        // No updates or deletes - immutable entity is saved already
        // Additional business rules can be added here

        log.info("Laureate {} processed successfully", technicalId);
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber {}", technicalId);

        // Validate subscriber fields
        if (!subscriber.isValid()) {
            log.error("Subscriber validation failed for {}", technicalId);
            return;
        }

        // Subscribers do not trigger further processing automatically

        log.info("Subscriber {} processed successfully", technicalId);
    }

    private void notifySubscribers(String jobTechnicalId, Job job) {
        log.info("Notifying subscribers for Job {}", jobTechnicalId);

        int notifiedCount = 0;

        for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
            Subscriber subscriber = entry.getValue();
            if (subscriber.getActive() != null && subscriber.getActive()) {
                // Simulate notification (e.g. send email or webhook call)
                log.info("Notifying subscriber {} at {}", subscriber.getSubscriberId(), subscriber.getContactValue());
                // Real implementation would send actual notifications here
                notifiedCount++;
            }
        }

        log.info("Notified {} subscribers for Job {}", notifiedCount, jobTechnicalId);
    }
}