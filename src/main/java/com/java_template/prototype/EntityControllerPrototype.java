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

        boolean ingestionSuccess = simulateIngestNobelData(job);

        if (ingestionSuccess) {
            job.setStatus("SUCCEEDED");
            log.info("Job {} ingestion succeeded", technicalId);
        } else {
            job.setStatus("FAILED");
            log.error("Job {} ingestion failed", technicalId);
        }
        job.setCompletedAt(LocalDateTime.now());

        simulateNotifySubscribers(job);

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

    private boolean simulateIngestNobelData(Job job) {
        // Simulate calling external API and ingesting laureates
        // For demonstration, create some dummy laureates

        for (int i = 0; i < 3; i++) {
            Laureate laureate = new Laureate();
            String laureateId = "laureate-" + laureateIdCounter.getAndIncrement();
            laureate.setLaureateId((int)(Math.random()*10000));
            laureate.setFirstname("Firstname" + i);
            laureate.setSurname("Surname" + i);
            laureate.setGender("unknown");
            laureate.setBorn(LocalDate.of(1970+i,1,1));
            laureate.setDied(null);
            laureate.setBorncountry("Country" + i);
            laureate.setBorncountrycode("CC");
            laureate.setBorncity("City" + i);
            laureate.setYear("20" + (10+i));
            laureate.setCategory("Category" + i);
            laureate.setMotivation("Motivation " + i);
            laureate.setAffiliationName("Affiliation " + i);
            laureate.setAffiliationCity("AffCity" + i);
            laureate.setAffiliationCountry("AffCountry" + i);

            String techId = laureateId;
            laureateCache.put(techId, laureate);
            processLaureate(techId, laureate);
        }
        job.setResultDetails("Ingested 3 dummy laureates");
        return true;
    }

    private void simulateNotifySubscribers(Job job) {
        // Simulate notifying all active subscribers
        long notifiedCount = subscriberCache.values().stream()
                .filter(sub -> sub.getActive() != null && sub.getActive())
                .count();
        log.info("Notified {} active subscribers for job {}", notifiedCount, job.getJobName());
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        simulateValidateLaureate(laureate);
        simulateEnrichLaureate(laureate);
        // Persistence is simulated by caching on creation
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
        // Normalize borncountrycode to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        log.info("Laureate enrichment completed for id {}", laureate.getLaureateId());
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        // Validate subscriber contact info
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank() ||
            subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
            log.error("Subscriber validation failed for technicalId {}", technicalId);
            throw new IllegalArgumentException("Subscriber contactType and contactAddress are required");
        }
        log.info("Processed subscriber {}", technicalId);
    }
}
