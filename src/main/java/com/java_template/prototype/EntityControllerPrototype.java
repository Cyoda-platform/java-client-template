package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    // --- Job Endpoints ---

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> request) {
        String jobName = request.get("jobName");
        if (jobName == null || jobName.isBlank()) {
            log.error("Job creation failed: jobName is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "jobName is required"));
        }
        String technicalId = "job-" + jobIdCounter.getAndIncrement();
        Job job = new Job();
        job.setJobName(jobName);
        job.setStatus("SCHEDULED");
        job.setCreatedAt(LocalDateTime.now());
        jobCache.put(technicalId, job);
        log.info("Job created with technicalId {}", technicalId);
        processJob(technicalId, job);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    // --- Laureate Endpoints ---

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            log.error("Laureate not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Laureate not found"));
        }
        return ResponseEntity.ok(laureate);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank() ||
            subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank() || subscriber.getActive() == null || !subscriber.getActive()) {
            log.error("Subscriber creation failed: contactType or contactAddress missing or subscriber inactive");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "contactType, contactAddress are required and subscriber must be active"));
        }
        String technicalId = "sub-" + subscriberIdCounter.getAndIncrement();
        subscriber.setSubscriberId(technicalId);
        subscriberCache.put(technicalId, subscriber);
        log.info("Subscriber created with technicalId {}", technicalId);
        processSubscriber(technicalId, subscriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        }
        return ResponseEntity.ok(subscriber);
    }

    // --- Processing Methods ---

    private void processJob(String technicalId, Job job) {
        simulateValidateJob(job);
        job.setStatus("INGESTING");
        log.info("Job {} status updated to INGESTING", technicalId);

        boolean ingestionSuccess = ingestNobelData(job);

        if (ingestionSuccess) {
            job.setStatus("SUCCEEDED");
            log.info("Job {} ingestion succeeded", technicalId);
        } else {
            job.setStatus("FAILED");
            log.error("Job {} ingestion failed", technicalId);
        }
        job.setCompletedAt(LocalDateTime.now());

        notifySubscribers(job);

        job.setStatus("NOTIFIED_SUBSCRIBERS");
        log.info("Job {} notifications sent to subscribers", technicalId);
    }

    private void simulateValidateJob(Job job) {
        if (job.getJobName() == null || job.getJobName().isBlank()) {
            log.error("Job validation failed: jobName is blank");
            throw new IllegalArgumentException("jobName must be provided");
        }
        log.info("Job validation succeeded for jobName {}", job.getJobName());
    }

    private boolean ingestNobelData(Job job) {
        try {
            // Example OpenDataSoft API URL for Nobel laureates
            String url = "https://public.opendatasoft.com/api/records/1.0/search/?dataset=laureates&q=&rows=100";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to fetch Nobel laureates data, status code: {}", response.getStatusCode());
                return false;
            }

            String json = response.getBody();
            JsonNode rootNode = objectMapper.readTree(json);
            JsonNode records = rootNode.path("records");

            if (!records.isArray()) {
                log.error("No records array found in Nobel laureates data");
                return false;
            }

            for (JsonNode recordNode : records) {
                JsonNode fields = recordNode.path("fields");
                Laureate laureate = new Laureate();

                laureate.setLaureateId(laureateIdCounter.getAndIncrement());
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

                String techId = "laureate-" + laureate.getLaureateId();
                laureateCache.put(techId, laureate);
                processLaureate(techId, laureate);
            }
            job.setResultDetails("Ingested " + records.size() + " laureates from external datasource.");
            return true;
        } catch (Exception e) {
            log.error("Exception during ingestion of Nobel laureates: {}", e.getMessage(), e);
            return false;
        }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
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
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private void notifySubscribers(Job job) {
        long notifiedCount = subscriberCache.values().stream()
                .filter(sub -> sub.getActive() != null && sub.getActive())
                .count();
        log.info("Notified {} active subscribers for job {}", notifiedCount, job.getJobName());
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        simulateValidateLaureate(laureate);
        simulateEnrichLaureate(laureate);
        log.info("Processed laureate {}", technicalId);
    }

    private void simulateValidateLaureate(Laureate laureate) {
        if (laureate.getLaureateId() == null || laureate.getFirstname() == null || laureate.getFirstname().isBlank()
            || laureate.getSurname() == null || laureate.getSurname().isBlank()
            || laureate.getYear() == null || laureate.getYear().isBlank()
            || laureate.getCategory() == null || laureate.getCategory().isBlank()) {
            log.error("Laureate validation failed: required fields missing");
            throw new IllegalArgumentException("Required laureate fields missing");
        }
        log.info("Laureate validation succeeded for id {}", laureate.getLaureateId());
    }

    private void simulateEnrichLaureate(Laureate laureate) {
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        log.info("Laureate enrichment completed for id {}", laureate.getLaureateId());
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank() ||
            subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
            log.error("Subscriber validation failed for technicalId {}", technicalId);
            throw new IllegalArgumentException("Subscriber contactType and contactAddress are required");
        }
        log.info("Processed subscriber {}", technicalId);
    }
}
