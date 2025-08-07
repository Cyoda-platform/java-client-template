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
import java.time.ZoneOffset;

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

    // ------------- JOB ENDPOINTS ----------------
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob() {
        try {
            // Generate technicalId for Job
            String technicalId = "job-" + jobIdCounter.getAndIncrement();

            Job job = new Job();
            job.setJobId(technicalId);
            job.setStatus("SCHEDULED");
            job.setCreatedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            job.setStartedAt(null);
            job.setFinishedAt(null);
            job.setErrorMessage(null);

            jobCache.put(technicalId, job);
            log.info("Job created with ID {}", technicalId);

            processJob(technicalId, job);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to create job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // ------------- SUBSCRIBER ENDPOINTS ----------------
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            // Validate contact info
            if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()
                    || subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
                log.error("Invalid subscriber contact info");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String technicalId = "subscriber-" + subscriberIdCounter.getAndIncrement();
            subscriber.setSubscriberId(technicalId);
            if (subscriber.getActive() == null) {
                subscriber.setActive(true);
            }
            subscriberCache.put(technicalId, subscriber);
            log.info("Subscriber created with ID {}", technicalId);

            processSubscriber(technicalId, subscriber);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to create subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<Subscriber>> getAllSubscribers() {
        List<Subscriber> list = new ArrayList<>(subscriberCache.values());
        return ResponseEntity.ok(list);
    }

    // ------------- LAUREATE ENDPOINTS ----------------
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(laureate);
    }

    @GetMapping("/laureates")
    public ResponseEntity<List<Laureate>> getLaureates(@RequestParam(required = false) String year,
                                                      @RequestParam(required = false) String category) {
        List<Laureate> result = new ArrayList<>();
        for (Laureate l : laureateCache.values()) {
            boolean matches = true;
            if (year != null && !year.isBlank() && !year.equals(l.getYear())) {
                matches = false;
            }
            if (category != null && !category.isBlank() && !category.equalsIgnoreCase(l.getCategory())) {
                matches = false;
            }
            if (matches) {
                result.add(l);
            }
        }
        return ResponseEntity.ok(result);
    }

    // ------------------- PROCESS METHODS ----------------------

    private void processJob(String technicalId, Job job) {
        log.info("Processing job {}", technicalId);

        try {
            // Validation processor simulation
            if (!simulateJobValidation(job)) {
                job.setStatus("FAILED");
                job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
                job.setErrorMessage("Job validation failed");
                log.error("Job validation failed for job {}", technicalId);
                return;
            }
            job.setStatus("INGESTING");
            job.setStartedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            log.info("Job {} status set to INGESTING", technicalId);

            // Call OpenDataSoft API to ingest Nobel laureates data
            String apiUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=1000";
            SpringResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, HttpEntity.EMPTY, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                job.setStatus("FAILED");
                job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
                job.setErrorMessage("Failed to fetch data from OpenDataSoft API. HTTP status: " + response.getStatusCodeValue());
                log.error("Failed to fetch data from API, status {}", response.getStatusCodeValue());
                return;
            }

            String body = response.getBody();
            JsonNode root = objectMapper.readTree(body);
            JsonNode records = root.path("records");

            if (records.isMissingNode() || !records.isArray()) {
                job.setStatus("FAILED");
                job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
                job.setErrorMessage("Malformed API response: missing records array");
                log.error("Malformed API response: missing records array");
                return;
            }

            // For each laureate record, create Laureate entity and process it
            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                if (fields.isMissingNode()) {
                    continue; // skip malformed record
                }
                Laureate laureate = new Laureate();
                // Assign technicalId for laureate
                String laureateTechnicalId = "laureate-" + laureateIdCounter.getAndIncrement();
                laureate.setLaureateId(laureateTechnicalId);

                // Parse and set fields with null-safe extraction
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

                laureateCache.put(laureateTechnicalId, laureate);
                log.info("Laureate entity created with ID {}", laureateTechnicalId);

                processLaureate(laureateTechnicalId, laureate);
            }

            job.setStatus("SUCCEEDED");
            job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            log.info("Job {} ingestion succeeded", technicalId);

            // Notify all active subscribers
            notifySubscribers();

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            log.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", technicalId);

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setFinishedAt(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            job.setErrorMessage("Exception during job processing: " + e.getMessage());
            log.error("Exception during job processing for job {}", technicalId, e);
        }
    }

    private boolean simulateJobValidation(Job job) {
        // Simulate job parameter validation: currently no parameters, always valid
        return job != null && job.getStatus() != null && job.getStatus().equals("SCHEDULED");
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        log.info("Processing laureate {}", technicalId);

        if (!simulateLaureateValidation(laureate)) {
            log.error("Laureate validation failed for {}", technicalId);
            return; // skip invalid laureate
        }

        simulateLaureateEnrichment(laureate);

        // Immutable persistence simulation: already saved in cache
        log.info("Laureate {} processed and saved", technicalId);
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
        // Example enrichment: normalize country code to uppercase if present
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing subscriber {}", technicalId);
        if (!simulateSubscriberValidation(subscriber)) {
            log.error("Subscriber validation failed for {}", technicalId);
            return; // skip invalid subscriber
        }
        // Immutable persistence simulated by cache put already done
        log.info("Subscriber {} processed and saved", technicalId);
    }

    private boolean simulateSubscriberValidation(Subscriber subscriber) {
        if (subscriber == null) return false;
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) return false;
        return true;
    }

    private void notifySubscribers() {
        log.info("Notifying active subscribers");
        int notifiedCount = 0;
        for (Subscriber subscriber : subscriberCache.values()) {
            if (Boolean.TRUE.equals(subscriber.getActive())) {
                // Simulate sending notification
                log.info("Notified subscriber {} at {}", subscriber.getSubscriberId(), subscriber.getContactAddress());
                notifiedCount++;
            }
        }
        log.info("Total subscribers notified: {}", notifiedCount);
    }
}