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

    // -------- JOB Endpoints --------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> request) {
        String jobName = request.get("jobName");
        if (jobName == null || jobName.isBlank()) {
            log.error("Job creation failed: jobName is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName is required"));
        }
        String technicalId = String.valueOf(jobIdCounter.getAndIncrement());
        Job job = new Job();
        job.setJobName(jobName);
        job.setStatus("SCHEDULED");
        job.setCreatedAt(java.time.Instant.now().toString());
        jobCache.put(technicalId, job);
        log.info("Job created with technicalId: {}", technicalId);

        processJob(technicalId, job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    // -------- LAUREATE Endpoints --------

    // No POST, as per requirements - created immutably by Job processing

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            log.error("Laureate not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
        }
        return ResponseEntity.ok(laureate);
    }

    // Optional GET /laureates with filters omitted for prototype simplicity

    // -------- SUBSCRIBER Endpoints --------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber.getSubscriberName() == null || subscriber.getSubscriberName().isBlank()) {
            log.error("Subscriber creation failed: subscriberName is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "subscriberName is required"));
        }
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
            log.error("Subscriber creation failed: contactType is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType is required"));
        }
        if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
            log.error("Subscriber creation failed: contactAddress is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactAddress is required"));
        }
        if (subscriber.getSubscribedCategories() == null) {
            subscriber.setSubscribedCategories("");
        }
        if (subscriber.getActive() == null || subscriber.getActive().isBlank()) {
            subscriber.setActive("true");
        }
        String technicalId = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriberCache.put(technicalId, subscriber);
        log.info("Subscriber created with technicalId: {}", technicalId);

        processSubscriber(technicalId, subscriber);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        }
        return ResponseEntity.ok(subscriber);
    }

    // -------- PROCESS METHODS --------

    private void processJob(String technicalId, Job job) {
        log.info("Processing Job with technicalId: {} and jobName: {}", technicalId, job.getJobName());
        simulateJobValidationProcessor(job);
        job.setStatus("INGESTING");
        log.info("Job status updated to INGESTING for technicalId: {}", technicalId);

        boolean ingestionSuccess = false;
        try {
            List<Laureate> laureates = ingestLaureatesFromOpenDataSoft();
            ingestionSuccess = laureates != null && !laureates.isEmpty();

            for (Laureate laureate : laureates) {
                String laureateId = String.valueOf(laureateIdCounter.getAndIncrement());
                laureateCache.put(laureateId, laureate);
                log.info("Laureate created with technicalId: {} for laureate id: {}", laureateId, laureate.getId());
                processLaureate(laureateId, laureate);
            }
        } catch (Exception e) {
            log.error("Exception during laureate ingestion: {}", e.getMessage());
            ingestionSuccess = false;
        }

        if (ingestionSuccess) {
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(java.time.Instant.now().toString());
            job.setDetails("Job completed successfully");
            log.info("Job ingestion succeeded for technicalId: {}", technicalId);
        } else {
            job.setStatus("FAILED");
            job.setFinishedAt(java.time.Instant.now().toString());
            job.setDetails("Job ingestion failed");
            log.error("Job ingestion failed for technicalId: {}", technicalId);
        }

        // Notify Subscribers
        simulateSubscriberNotificationProcessor(job);

        job.setStatus("NOTIFIED_SUBSCRIBERS");
        log.info("Job status updated to NOTIFIED_SUBSCRIBERS for technicalId: {}", technicalId);
    }

    private List<Laureate> ingestLaureatesFromOpenDataSoft() throws Exception {
        String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=10"; // limit to 10 for prototype
        String response = restTemplate.getForObject(url, String.class);
        JsonNode root = objectMapper.readTree(response);
        JsonNode records = root.path("records");
        List<Laureate> laureates = new ArrayList<>();
        if (records.isArray()) {
            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                Laureate laureate = new Laureate();
                laureate.setId(fields.path("id").asInt());
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
                laureates.add(laureate);
            }
        }
        return laureates;
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        log.info("Processing Laureate with technicalId: {} and id: {}", technicalId, laureate.getId());
        boolean valid = simulateLaureateValidationProcessor(laureate);
        if (!valid) {
            log.error("Laureate validation failed for technicalId: {}", technicalId);
            return;
        }
        simulateLaureateEnrichmentProcessor(laureate);
        log.info("Laureate processed and persisted immutably with technicalId: {}", technicalId);
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber with technicalId: {} and name: {}", technicalId, subscriber.getSubscriberName());
        boolean valid = simulateSubscriberValidationProcessor(subscriber);
        if (!valid) {
            log.error("Subscriber validation failed for technicalId: {}", technicalId);
            return;
        }
        if (!"true".equalsIgnoreCase(subscriber.getActive()) && !"false".equalsIgnoreCase(subscriber.getActive())) {
            subscriber.setActive("true"); // default to true if invalid value
        }
        log.info("Subscriber processed and persisted immutably with technicalId: {}", technicalId);
    }

    // -------- SIMULATED PROCESSORS & CRITERIA --------

    private void simulateJobValidationProcessor(Job job) {
        // Validate jobName non-null and non-blank
        if (job.getJobName() == null || job.getJobName().isBlank()) {
            throw new IllegalArgumentException("JobName is required");
        }
        log.info("JobValidationProcessor passed for jobName: {}", job.getJobName());
    }

    private boolean simulateLaureateValidationProcessor(Laureate laureate) {
        // Check mandatory fields: id, firstname, surname, year, category
        if (laureate.getId() == 0) return false;
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        log.info("LaureateValidationProcessor passed for laureate id: {}", laureate.getId());
        return true;
    }

    private void simulateLaureateEnrichmentProcessor(Laureate laureate) {
        // Normalize country codes to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        // Could calculate age or other derived data here if needed
        log.info("LaureateEnrichmentProcessor completed for laureate id: {}", laureate.getId());
    }

    private boolean simulateSubscriberValidationProcessor(Subscriber subscriber) {
        // Validate contactType and contactAddress formats (basic)
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) return false;
        // Additional format checks could be added here
        log.info("SubscriberValidationProcessor passed for subscriber: {}", subscriber.getSubscriberName());
        return true;
    }

    private void simulateSubscriberNotificationProcessor(Job job) {
        log.info("Notifying subscribers for job: {}", job.getJobName());
        // For each active subscriber, check if their subscribedCategories intersect with laureate categories ingested
        Set<String> laureateCategories = new HashSet<>();
        for (Laureate laureate : laureateCache.values()) {
            laureateCategories.add(laureate.getCategory());
        }
        for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
            Subscriber subscriber = entry.getValue();
            if (!"true".equalsIgnoreCase(subscriber.getActive())) continue;
            String[] categories = subscriber.getSubscribedCategories() != null ?
                    subscriber.getSubscribedCategories().split(",") : new String[0];
            boolean interested = false;
            for (String cat : categories) {
                if (laureateCategories.contains(cat.trim())) {
                    interested = true;
                    break;
                }
            }
            if (interested) {
                // Simulate sending notification
                log.info("Notified subscriber: {} at {}", subscriber.getSubscriberName(), subscriber.getContactAddress());
            }
        }
    }
}