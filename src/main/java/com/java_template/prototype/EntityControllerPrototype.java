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
import org.springframework.http.ResponseEntity as HttpResponseEntity;

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
    private static final String OPEN_DATA_SOFT_API = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    // POST /prototype/jobs - create Job, trigger ingestion
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Job job) {
        if (job == null || !job.isValid()) {
            log.error("Invalid Job data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "job-" + jobIdCounter.getAndIncrement();
        job.setStatus("PENDING");
        jobCache.put(technicalId, job);
        log.info("Job created with technicalId {}", technicalId);

        try {
            processJob(technicalId, job);
        } catch (Exception e) {
            log.error("Error processing job {}: {}", technicalId, e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/jobs/{id} - retrieve Job by technicalId
    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable String id) {
        Job job = jobCache.get(id);
        if (job == null) {
            log.error("Job not found with technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/subscribers - create Subscriber
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber == null || !subscriber.isValid()) {
            log.error("Invalid Subscriber data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "sub-" + subscriberIdCounter.getAndIncrement();
        subscriberCache.put(technicalId, subscriber);
        log.info("Subscriber created with technicalId {}", technicalId);

        try {
            processSubscriber(technicalId, subscriber);
        } catch (Exception e) {
            log.error("Error processing subscriber {}: {}", technicalId, e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/subscribers/{id} - retrieve Subscriber
    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber not found with technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // GET /prototype/laureates/{id} - retrieve Laureate (no POST endpoint)
    @GetMapping("/laureates/{id}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String id) {
        Laureate laureate = laureateCache.get(id);
        if (laureate == null) {
            log.error("Laureate not found with technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(laureate);
    }

    // processJob method - ingestion and notification workflow
    private void processJob(String technicalId, Job job) {
        log.info("Starting processJob for {}", technicalId);
        try {
            // Update status to INGESTING and set startedAt timestamp
            job.setStatus("INGESTING");
            job.setStartedAt(java.time.Instant.now().toString());
            jobCache.put(technicalId, job);

            // Call OpenDataSoft API for Nobel laureates data
            HttpResponseEntity<Map> response = restTemplate.getForEntity(OPEN_DATA_SOFT_API, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch data from OpenDataSoft API");
            }

            Map body = response.getBody();
            if (body == null || !body.containsKey("records")) {
                throw new RuntimeException("Invalid data received from OpenDataSoft API");
            }

            List records = (List) body.get("records");
            if (records == null) records = Collections.emptyList();

            // For each laureate record, parse and save new Laureate entity immutably
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
                    String laureateId = "laureate-" + laureateIdCounter.getAndIncrement();
                    laureateCache.put(laureateId, laureate);
                    processLaureate(laureateId, laureate);
                } else {
                    log.error("Invalid laureate data skipped: {}", laureate);
                }
            }

            // Update job status to SUCCEEDED and finishedAt timestamp
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(java.time.Instant.now().toString());
            jobCache.put(technicalId, job);

            // Notify all active subscribers
            notifySubscribers();

            // Update job status to NOTIFIED
            job.setStatus("NOTIFIED");
            jobCache.put(technicalId, job);

            log.info("processJob completed successfully for {}", technicalId);

        } catch (Exception e) {
            log.error("processJob failed for {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setMessage(e.getMessage());
            job.setFinishedAt(java.time.Instant.now().toString());
            jobCache.put(technicalId, job);

            try {
                notifySubscribers();
                job.setStatus("NOTIFIED");
                jobCache.put(technicalId, job);
            } catch (Exception notifyEx) {
                log.error("Failed to notify subscribers after job failure: {}", notifyEx.getMessage());
            }
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        // Validation and enrichment already done in isValid() and ingestion step
        log.info("Processed Laureate {}", technicalId);
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        // No further processing required on creation as per requirements
        log.info("Processed Subscriber {}", technicalId);
    }

    private void notifySubscribers() {
        log.info("Notifying active subscribers...");
        for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
            Subscriber subscriber = entry.getValue();
            if (Boolean.TRUE.equals(subscriber.getActive())) {
                // For demo, just log notification sending
                log.info("Notifying subscriber {} via {}: {}", entry.getKey(), subscriber.getContactType(), subscriber.getContactValue());
                // Real implementation could send email or webhook here
            }
        }
    }
}