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
import org.springframework.http.ResponseEntity as SpringResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.io.IOException;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counters for entities
    private final ConcurrentHashMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Laureate> laureateCache = new ConcurrentHashMap<>();
    private final AtomicLong laureateIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------- JOB ENDPOINTS -------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Long>> createJob(@RequestBody Map<String, String> request) {
        String externalId = request.get("externalId");
        if (externalId == null || externalId.isBlank()) {
            log.error("Job creation failed: externalId is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(jobIdCounter.getAndIncrement());
        Job job = new Job();
        job.setId(Long.parseLong(technicalId));
        job.setExternalId(externalId);
        job.setState("SCHEDULED");
        job.setCreatedAt(OffsetDateTime.now());
        jobCache.put(technicalId, job);
        log.info("Job created with technicalId {}", technicalId);

        // Trigger processing asynchronously
        new Thread(() -> processJob(technicalId, job)).start();

        Map<String, Long> response = new HashMap<>();
        response.put("technicalId", Long.parseLong(technicalId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable("id") String id) {
        Job job = jobCache.get(id);
        if (job == null) {
            log.error("Job with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // ------------------- LAUREATE ENDPOINTS -------------------

    @GetMapping("/laureates/{id}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable("id") String id) {
        Laureate laureate = laureateCache.get(id);
        if (laureate == null) {
            log.error("Laureate with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(laureate);
    }

    // ------------------- SUBSCRIBER ENDPOINTS -------------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, Long>> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber.getContactEmail() == null || subscriber.getContactEmail().isBlank()) {
            log.error("Subscriber creation failed: contactEmail is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (subscriber.getActive() == null) {
            log.error("Subscriber creation failed: active flag is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriber.setId(Long.parseLong(technicalId));
        subscriberCache.put(technicalId, subscriber);
        log.info("Subscriber created with technicalId {}", technicalId);

        // Process subscriber asynchronously
        new Thread(() -> processSubscriber(technicalId, subscriber)).start();

        Map<String, Long> response = new HashMap<>();
        response.put("technicalId", Long.parseLong(technicalId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable("id") String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // ------------------- PROCESS METHODS -------------------

    private void processJob(String technicalId, Job job) {
        try {
            log.info("Processing job {} - state: {}", technicalId, job.getState());

            simulateValidationProcessorJob(job);

            job.setState("INGESTING");
            jobCache.put(technicalId, job);
            log.info("Job {} state transitioned to INGESTING", technicalId);

            // Fetch laureates from OpenDataSoft API
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";
            SpringResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                job.setState("FAILED");
                job.setResultSummary("Failed to fetch laureates data: HTTP status " + response.getStatusCodeValue());
                job.setCompletedAt(OffsetDateTime.now());
                jobCache.put(technicalId, job);
                log.error("Job {} failed during data fetch", technicalId);
                return;
            }

            String body = response.getBody();
            JsonNode root = objectMapper.readTree(body);
            JsonNode records = root.path("records");
            if (records.isMissingNode() || !records.isArray()) {
                job.setState("FAILED");
                job.setResultSummary("Invalid laureates data structure");
                job.setCompletedAt(OffsetDateTime.now());
                jobCache.put(technicalId, job);
                log.error("Job {} failed due to invalid laureates data structure", technicalId);
                return;
            }

            int processedCount = 0;
            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                if (fields.isMissingNode()) continue;

                Laureate laureate = new Laureate();
                laureate.setId(laureateIdCounter.getAndIncrement());
                laureate.setLaureateId(fields.path("id").asInt(0));
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

                String laureateTechnicalId = String.valueOf(laureate.getId());
                laureateCache.put(laureateTechnicalId, laureate);
                processLaureate(laureateTechnicalId, laureate);
                processedCount++;
            }

            job.setState("SUCCEEDED");
            job.setResultSummary("Ingested " + processedCount + " laureates");
            job.setCompletedAt(OffsetDateTime.now());
            jobCache.put(technicalId, job);
            log.info("Job {} ingestion succeeded with {} laureates", technicalId, processedCount);

            notifySubscribers(technicalId, job);

            job.setState("NOTIFIED_SUBSCRIBERS");
            jobCache.put(technicalId, job);
            log.info("Job {} state transitioned to NOTIFIED_SUBSCRIBERS", technicalId);

        } catch (IOException e) {
            job.setState("FAILED");
            job.setResultSummary("Exception during processing: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobCache.put(technicalId, job);
            log.error("Job {} failed with exception: {}", technicalId, e.getMessage());
        } catch (Exception e) {
            job.setState("FAILED");
            job.setResultSummary("Unexpected error: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobCache.put(technicalId, job);
            log.error("Job {} failed with unexpected error: {}", technicalId, e.getMessage());
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        log.info("Processing laureate {}", technicalId);

        if (!simulateValidationProcessorLaureate(laureate)) {
            log.error("Laureate {} validation failed", technicalId);
            return;
        }

        simulateEnrichmentProcessorLaureate(laureate);

        // Already persisted immutably in cache

        log.info("Laureate {} processed successfully", technicalId);
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing subscriber {}", technicalId);
        if (!simulateValidationProcessorSubscriber(subscriber)) {
            log.error("Subscriber {} validation failed", technicalId);
            return;
        }
        // Already persisted in cache
        log.info("Subscriber {} processed successfully", technicalId);
    }

    // ------------------- SIMULATE PROCESSORS AND CRITERIA -------------------

    private void simulateValidationProcessorJob(Job job) {
        if (job.getExternalId() == null || job.getExternalId().isBlank()) {
            throw new IllegalArgumentException("Job externalId must not be blank");
        }
        if (!"SCHEDULED".equals(job.getState())) {
            throw new IllegalStateException("Job state must be SCHEDULED to start processing");
        }
    }

    private boolean simulateValidationProcessorLaureate(Laureate laureate) {
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getGender() == null || laureate.getGender().isBlank()) return false;
        if (laureate.getBorn() == null || laureate.getBorn().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        return true;
    }

    private void simulateEnrichmentProcessorLaureate(Laureate laureate) {
        try {
            if (laureate.getBorn() != null && !laureate.getBorn().isBlank()) {
                LocalDate bornDate = LocalDate.parse(laureate.getBorn());
                LocalDate diedDate = null;
                if (laureate.getDied() != null && !laureate.getDied().isBlank()) {
                    diedDate = LocalDate.parse(laureate.getDied());
                }
                int age = (diedDate != null) ? Period.between(bornDate, diedDate).getYears() : Period.between(bornDate, LocalDate.now()).getYears();
                laureate.setCalculatedAge(age);
            }
        } catch (Exception e) {
            log.error("Error calculating age for laureate {}: {}", laureate.getId(), e.getMessage());
            laureate.setCalculatedAge(null);
        }
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private boolean simulateValidationProcessorSubscriber(Subscriber subscriber) {
        if (subscriber.getContactEmail() == null || subscriber.getContactEmail().isBlank()) return false;
        if (!subscriber.getContactEmail().contains("@")) return false;
        if (subscriber.getWebhookUrl() != null && !subscriber.getWebhookUrl().isBlank()) {
            if (!subscriber.getWebhookUrl().startsWith("http://") && !subscriber.getWebhookUrl().startsWith("https://")) {
                return false;
            }
        }
        return true;
    }

    // ------------------- NOTIFICATIONS -------------------

    private void notifySubscribers(String jobId, Job job) {
        log.info("Notifying subscribers for job {}", jobId);
        subscriberCache.values().stream()
            .filter(Subscriber::getActive)
            .forEach(subscriber -> {
                try {
                    if (subscriber.getWebhookUrl() != null && !subscriber.getWebhookUrl().isBlank()) {
                        sendWebhookNotification(subscriber.getWebhookUrl(), job);
                    } else {
                        sendEmailNotification(subscriber.getContactEmail(), job);
                    }
                } catch (Exception e) {
                    log.error("Failed to notify subscriber {}: {}", subscriber.getId(), e.getMessage());
                }
            });
    }

    private void sendEmailNotification(String email, Job job) {
        log.info("Sending email notification to {} for job {} with state {}", email, job.getId(), job.getState());
        // Simulated email sending
    }

    private void sendWebhookNotification(String webhookUrl, Job job) {
        log.info("Sending webhook notification to {} for job {} with state {}", webhookUrl, job.getId(), job.getState());
        // Simulated webhook sending
    }
}