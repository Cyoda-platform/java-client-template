package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.time.Instant;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;

import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.ExternalApiData;
import com.java_template.application.entity.EmailDispatchRecord;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, DigestRequestJob> digestRequestJobCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, ExternalApiData> externalApiDataCache = new ConcurrentHashMap<>();
    private final AtomicLong externalApiDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, EmailDispatchRecord> emailDispatchRecordCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchRecordIdCounter = new AtomicLong(1);

    // POST /prototype/digest-request-jobs - create a new digest request job
    @PostMapping("/digest-request-jobs")
    public ResponseEntity<Map<String, String>> createDigestRequestJob(@RequestBody DigestRequestJob request) {
        // Validate required fields
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            log.error("Email is blank or missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required"));
        }
        if (!request.isValid()) {
            log.error("DigestRequestJob entity validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid DigestRequestJob entity"));
        }
        // Generate technical ID
        String technicalId = String.valueOf(digestRequestJobIdCounter.getAndIncrement());
        request.setStatus("PENDING");
        request.setCreatedAt(Instant.now().toString());
        // Save to cache
        digestRequestJobCache.put(technicalId, request);
        log.info("Created DigestRequestJob with technicalId: {}", technicalId);

        // Trigger processing
        processDigestRequestJob(technicalId, request);

        // Return technicalId only
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/digest-request-jobs/{id} - retrieve job by technicalId
    @GetMapping("/digest-request-jobs/{id}")
    public ResponseEntity<DigestRequestJob> getDigestRequestJob(@PathVariable String id) {
        DigestRequestJob job = digestRequestJobCache.get(id);
        if (job == null) {
            log.error("DigestRequestJob not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/external-api-data/{jobTechnicalId} - retrieve external API data by jobTechnicalId
    @GetMapping("/external-api-data/{jobTechnicalId}")
    public ResponseEntity<ExternalApiData> getExternalApiData(@PathVariable String jobTechnicalId) {
        ExternalApiData data = externalApiDataCache.get(jobTechnicalId);
        if (data == null) {
            log.error("ExternalApiData not found for jobTechnicalId: {}", jobTechnicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(data);
    }

    // GET /prototype/email-dispatch-record/{jobTechnicalId} - retrieve email dispatch status by jobTechnicalId
    @GetMapping("/email-dispatch-record/{jobTechnicalId}")
    public ResponseEntity<EmailDispatchRecord> getEmailDispatchRecord(@PathVariable String jobTechnicalId) {
        EmailDispatchRecord record = emailDispatchRecordCache.get(jobTechnicalId);
        if (record == null) {
            log.error("EmailDispatchRecord not found for jobTechnicalId: {}", jobTechnicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(record);
    }

    // Business logic for processing DigestRequestJob
    private void processDigestRequestJob(String technicalId, DigestRequestJob job) {
        log.info("Processing DigestRequestJob with ID: {}", technicalId);

        // Validate email format (simple check)
        if (job.getEmail().isBlank() || !job.getEmail().contains("@")) {
            log.error("Invalid email format for job ID: {}", technicalId);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            digestRequestJobCache.put(technicalId, job);
            return;
        }

        // Retrieve data from external API
        String apiDataPayload = "";
        try {
            apiDataPayload = fetchExternalApiData();
        } catch (Exception e) {
            log.error("Failed to fetch external API data for job ID: {} - {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            digestRequestJobCache.put(technicalId, job);
            return;
        }

        // Save ExternalApiData entity
        ExternalApiData apiData = new ExternalApiData();
        apiData.setJobTechnicalId(technicalId);
        apiData.setDataPayload(apiDataPayload);
        apiData.setRetrievedAt(Instant.now().toString());
        externalApiDataCache.put(technicalId, apiData);
        log.info("Saved ExternalApiData for job ID: {}", technicalId);

        // Proceed with email dispatch
        EmailDispatchRecord emailRecord = new EmailDispatchRecord();
        emailRecord.setJobTechnicalId(technicalId);
        emailRecord.setEmail(job.getEmail());
        emailRecord.setDispatchStatus("PENDING");
        emailDispatchRecordCache.put(technicalId, emailRecord);
        log.info("Created EmailDispatchRecord for job ID: {}", technicalId);

        processEmailDispatchRecord(technicalId, emailRecord);

        // Update job status to COMPLETED
        job.setStatus("COMPLETED");
        job.setCompletedAt(Instant.now().toString());
        digestRequestJobCache.put(technicalId, job);
        log.info("DigestRequestJob with ID: {} completed successfully", technicalId);
    }

    // Fetch data from external API (petstore.swagger.io)
    private String fetchExternalApiData() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        // Example endpoint: GET /pet/findByStatus?status=available
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://petstore.swagger.io/v2/pet/findByStatus?status=available"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("External API call failed with status: " + response.statusCode());
        }
        return response.body();
    }

    // Business logic for processing EmailDispatchRecord
    private void processEmailDispatchRecord(String technicalId, EmailDispatchRecord record) {
        log.info("Processing EmailDispatchRecord with job ID: {}", technicalId);

        // Simulate email sending (in real app, integrate with email service)
        try {
            // Simulate sending delay
            Thread.sleep(500);
            // Mark as SENT
            record.setDispatchStatus("SENT");
            record.setSentAt(Instant.now().toString());
            emailDispatchRecordCache.put(technicalId, record);
            log.info("Email sent successfully to {} for job ID: {}", record.getEmail(), technicalId);
        } catch (InterruptedException e) {
            log.error("Email sending interrupted for job ID: {}", technicalId);
            record.setDispatchStatus("FAILED");
            emailDispatchRecordCache.put(technicalId, record);
        }
    }
}