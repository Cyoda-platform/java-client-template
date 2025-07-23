package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, com.java_template.application.entity.DataIngestionJob> dataIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong dataIngestionJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.AnalysisReport> analysisReportCache = new ConcurrentHashMap<>();
    private final AtomicLong analysisReportIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // DataIngestionJob endpoints

    @PostMapping("/dataIngestionJob")
    public ResponseEntity<?> createDataIngestionJob(@RequestBody com.java_template.application.entity.DataIngestionJob job) {
        if (job == null || job.getCsvUrl() == null || job.getCsvUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("csvUrl is required");
        }

        String newId = String.valueOf(dataIngestionJobIdCounter.getAndIncrement());
        job.setId(newId);
        job.setStatus("PENDING");
        job.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        dataIngestionJobCache.put(newId, job);

        processDataIngestionJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", job.getId(), "status", job.getStatus()));
    }

    @GetMapping("/dataIngestionJob/{id}")
    public ResponseEntity<?> getDataIngestionJob(@PathVariable String id) {
        com.java_template.application.entity.DataIngestionJob job = dataIngestionJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DataIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // AnalysisReport endpoints

    @PostMapping("/analysisReport")
    public ResponseEntity<?> createAnalysisReport(@RequestBody com.java_template.application.entity.AnalysisReport report) {
        if (report == null || report.getJobId() == null || report.getJobId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("jobId is required");
        }

        String newId = String.valueOf(analysisReportIdCounter.getAndIncrement());
        report.setId(newId);
        report.setStatus("PENDING");
        report.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        analysisReportCache.put(newId, report);

        processAnalysisReport(report);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", report.getId(), "status", report.getStatus()));
    }

    @GetMapping("/analysisReport/{id}")
    public ResponseEntity<?> getAnalysisReport(@PathVariable String id) {
        com.java_template.application.entity.AnalysisReport report = analysisReportCache.get(id);
        if (report == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AnalysisReport not found");
        }
        return ResponseEntity.ok(report);
    }

    // Subscriber endpoints

    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody com.java_template.application.entity.Subscriber subscriber) {
        if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("email is required");
        }

        String newId = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriber.setId(newId);
        subscriber.setSubscribedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        subscriberCache.put(newId, subscriber);

        processSubscriber(subscriber);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", subscriber.getId()));
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        com.java_template.application.entity.Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<?> getAllSubscribers() {
        return ResponseEntity.ok(new ArrayList<>(subscriberCache.values()));
    }

    // Process methods with actual business logic

    private void processDataIngestionJob(com.java_template.application.entity.DataIngestionJob job) {
        log.info("Processing DataIngestionJob with ID: {}", job.getId());
        try {
            job.setStatus("PROCESSING");
            // Validate CSV URL accessibility
            if (job.getCsvUrl() == null || job.getCsvUrl().isBlank()) {
                throw new IllegalArgumentException("CSV URL is blank");
            }
            // Simulate download and parse CSV data (in real app, use HTTP client and pandas equivalent)
            // For prototype, we just simulate delay and success
            Thread.sleep(500);

            job.setStatus("COMPLETED");

            // Create AnalysisReport entity linked to this job
            com.java_template.application.entity.AnalysisReport report = new com.java_template.application.entity.AnalysisReport();
            report.setJobId(job.getId());
            report.setSummaryStatistics("{ \"mean\": {}, \"median\": {}, \"stddev\": {} }"); // dummy JSON string
            report.setStatus("PENDING");
            report.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

            String reportId = String.valueOf(analysisReportIdCounter.getAndIncrement());
            report.setId(reportId);

            analysisReportCache.put(reportId, report);

            processAnalysisReport(report);

        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Failed to process DataIngestionJob with ID: {}, error: {}", job.getId(), e.getMessage());
        }
    }

    private void processAnalysisReport(com.java_template.application.entity.AnalysisReport report) {
        log.info("Processing AnalysisReport with ID: {}", report.getId());
        try {
            report.setStatus("PROCESSING");
            // Simulate calculation of summary statistics (already present in summaryStatistics field for prototype)
            Thread.sleep(300);

            report.setStatus("COMPLETED");

            // Send report via email to all Subscribers
            for (com.java_template.application.entity.Subscriber subscriber : subscriberCache.values()) {
                log.info("Sending report ID {} to subscriber {}", report.getId(), subscriber.getEmail());
                // In real app, send email here
            }

        } catch (Exception e) {
            report.setStatus("FAILED");
            log.error("Failed to process AnalysisReport with ID: {}, error: {}", report.getId(), e.getMessage());
        }
    }

    private void processSubscriber(com.java_template.application.entity.Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", subscriber.getId());
        // No additional processing needed for subscribers in this prototype
    }
}