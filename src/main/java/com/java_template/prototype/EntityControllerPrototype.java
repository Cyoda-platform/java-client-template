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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    // ----------------- JOB ENDPOINTS -----------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String,String>> createJob(@RequestBody Job jobRequest) {
        if (jobRequest.getJobName() == null || jobRequest.getJobName().isBlank()
            || jobRequest.getScheduledTime() == null || jobRequest.getScheduledTime().isBlank()) {
            log.error("Job creation failed due to missing jobName or scheduledTime");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "job-" + jobIdCounter.getAndIncrement();
        Job job = new Job();
        job.setJobName(jobRequest.getJobName());
        job.setScheduledTime(jobRequest.getScheduledTime());
        job.setStatus("SCHEDULED");
        job.setCreatedAt(java.time.OffsetDateTime.now().toString());
        job.setResultSummary(null);
        jobCache.put(technicalId, job);
        log.info("Job created with technicalId {}", technicalId);

        // Trigger processing after creation
        processJob(technicalId, job);

        Map<String,String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // ----------------- LAUREATE ENDPOINTS -----------------

    // No POST endpoint for Laureate as per requirements

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            log.error("Laureate not found with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(laureate);
    }

    // ----------------- SUBSCRIBER ENDPOINTS -----------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String,String>> createSubscriber(@RequestBody Subscriber subscriberRequest) {
        if (subscriberRequest.getContactType() == null || subscriberRequest.getContactType().isBlank()
            || subscriberRequest.getContactValue() == null || subscriberRequest.getContactValue().isBlank()) {
            log.error("Subscriber creation failed due to missing contactType or contactValue");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "sub-" + subscriberIdCounter.getAndIncrement();
        Subscriber subscriber = new Subscriber();
        subscriber.setContactType(subscriberRequest.getContactType());
        subscriber.setContactValue(subscriberRequest.getContactValue());
        subscriber.setActive(subscriberRequest.getActive() != null ? subscriberRequest.getActive() : true);
        subscriberCache.put(technicalId, subscriber);
        log.info("Subscriber created with technicalId {}", technicalId);

        // Trigger processing after creation
        processSubscriber(technicalId, subscriber);

        Map<String,String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // ----------------- PROCESS METHODS -----------------

    private void processJob(String technicalId, Job job) {
        log.info("Processing Job {} with status {}", technicalId, job.getStatus());
        try {
            // Transition to INGESTING
            job.setStatus("INGESTING");
            jobCache.put(technicalId, job);
            log.info("Job {} status updated to INGESTING", technicalId);

            // Call OpenDataSoft API
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to fetch laureates data from API, status: {}", response.getStatusCode());
                job.setStatus("FAILED");
                job.setResultSummary("Failed to fetch laureates data: HTTP " + response.getStatusCode());
                jobCache.put(technicalId, job);
                return;
            }

            String body = response.getBody();
            JsonNode rootNode = objectMapper.readTree(body);
            JsonNode records = rootNode.path("records");
            if (!records.isArray()) {
                log.error("API laureates data 'records' is not an array");
                job.setStatus("FAILED");
                job.setResultSummary("Invalid laureates data format");
                jobCache.put(technicalId, job);
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

                    // Generate technicalId for laureate
                    String laureateTechId = "laureate-" + laureateIdCounter.getAndIncrement();
                    laureateCache.put(laureateTechId, laureate);

                    // Process Laureate
                    processLaureate(laureateTechId, laureate);

                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to process laureate record: {}", e.getMessage());
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
            jobCache.put(technicalId, job);
            log.info("Job {} status updated to {}", technicalId, job.getStatus());

            // Notify subscribers
            notifySubscribers(job);

            // Mark notified
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            jobCache.put(technicalId, job);
            log.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);

        } catch (Exception ex) {
            log.error("Exception in processJob: {}", ex.getMessage());
            job.setStatus("FAILED");
            job.setResultSummary("Exception during ingestion: " + ex.getMessage());
            jobCache.put(technicalId, job);
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        log.info("Processing Laureate {}", technicalId);
        // Validation Processor
        if (!validateLaureate(laureate)) {
            log.error("Validation failed for Laureate {}", technicalId);
            return;
        }
        // Enrichment Processor
        enrichLaureate(laureate);
        // Immutable record already saved in cache
        log.info("Laureate {} processed successfully", technicalId);
    }

    private boolean validateLaureate(Laureate laureate) {
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        return true;
    }

    private void enrichLaureate(Laureate laureate) {
        // Example enrichment: Normalize country code to uppercase if present
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber {}", technicalId);
        // Validation
        if (!validateSubscriber(subscriber)) {
            log.error("Validation failed for Subscriber {}", technicalId);
            return;
        }
        // Immutable record saved in cache already
        log.info("Subscriber {} processed successfully", technicalId);
    }

    private boolean validateSubscriber(Subscriber subscriber) {
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactValue() == null || subscriber.getContactValue().isBlank()) return false;
        return true;
    }

    private void notifySubscribers(Job job) {
        log.info("Notifying active subscribers for Job");
        int notifiedCount = 0;
        for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
            Subscriber subscriber = entry.getValue();
            if (subscriber.getActive() != null && subscriber.getActive()) {
                try {
                    // Simulate notification sending (e.g., email or webhook)
                    log.info("Notifying subscriber {} at {}", entry.getKey(), subscriber.getContactValue());
                    // In real implementation, send email or webhook call here
                    notifiedCount++;
                } catch (Exception e) {
                    log.error("Failed to notify subscriber {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        log.info("Notified {} subscribers for Job", notifiedCount);
    }
}