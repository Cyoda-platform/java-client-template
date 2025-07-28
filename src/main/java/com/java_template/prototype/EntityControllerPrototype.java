package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.DigestDataRecord;
import com.java_template.application.entity.DigestEmailRecord;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, DigestRequestJob> digestRequestJobCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestDataRecord> digestDataRecordCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataRecordIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestEmailRecord> digestEmailRecordCache = new ConcurrentHashMap<>();
    private final AtomicLong digestEmailRecordIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/digestRequestJob - Create new DigestRequestJob
    @PostMapping("/digestRequestJob")
    public ResponseEntity<?> createDigestRequestJob(@RequestBody DigestRequestJob request) {
        if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
            log.error("Invalid userEmail");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("userEmail is required and cannot be blank");
        }
        if (request.getEventMetadata() == null || request.getEventMetadata().isBlank()) {
            log.error("Invalid eventMetadata");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("eventMetadata is required and cannot be blank");
        }
        String technicalId = "job-" + digestRequestJobIdCounter.getAndIncrement();
        DigestRequestJob job = new DigestRequestJob();
        job.setUserEmail(request.getUserEmail());
        job.setEventMetadata(request.getEventMetadata());
        job.setStatus("PENDING");
        job.setCreatedAt(java.time.Instant.now().toString());
        digestRequestJobCache.put(technicalId, job);
        log.info("Created DigestRequestJob with technicalId {}", technicalId);

        // Trigger processing
        try {
            processDigestRequestJob(technicalId, job);
        } catch (Exception e) {
            log.error("Error processing DigestRequestJob {}: {}", technicalId, e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/digestRequestJob/{id} - Retrieve DigestRequestJob by technicalId
    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable("id") String technicalId) {
        DigestRequestJob job = digestRequestJobCache.get(technicalId);
        if (job == null) {
            log.error("DigestRequestJob not found for id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/digestDataRecord/{id} - Retrieve DigestDataRecord by technicalId
    @GetMapping("/digestDataRecord/{id}")
    public ResponseEntity<?> getDigestDataRecord(@PathVariable("id") String technicalId) {
        DigestDataRecord record = digestDataRecordCache.get(technicalId);
        if (record == null) {
            log.error("DigestDataRecord not found for id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestDataRecord not found");
        }
        return ResponseEntity.ok(record);
    }

    // GET /prototype/digestEmailRecord/{id} - Retrieve DigestEmailRecord by technicalId
    @GetMapping("/digestEmailRecord/{id}")
    public ResponseEntity<?> getDigestEmailRecord(@PathVariable("id") String technicalId) {
        DigestEmailRecord record = digestEmailRecordCache.get(technicalId);
        if (record == null) {
            log.error("DigestEmailRecord not found for id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestEmailRecord not found");
        }
        return ResponseEntity.ok(record);
    }

    // Business logic: processDigestRequestJob
    private void processDigestRequestJob(String technicalId, DigestRequestJob job) {
        log.info("Processing DigestRequestJob {}", technicalId);
        job.setStatus("PROCESSING");
        digestRequestJobCache.put(technicalId, job);

        // Parse eventMetadata for API endpoints or use default
        String metadata = job.getEventMetadata();
        // For simplicity, assume metadata contains JSON with "status" field for pet status
        String petStatus = "available"; // default
        try {
            java.util.Map<String, Object> metadataMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadata, Map.class);
            if (metadataMap.containsKey("status")) {
                petStatus = metadataMap.get("status").toString();
            }
        } catch (Exception e) {
            log.error("Failed to parse eventMetadata for job {}: {}", technicalId, e.getMessage());
        }

        // Fetch data from petstore API /pet/findByStatus?status={petStatus}
        String apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + petStatus;
        String apiResponse = "";
        try {
            apiResponse = restTemplate.getForObject(apiUrl, String.class);
        } catch (Exception e) {
            log.error("Failed to fetch data from external API for job {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setCompletedAt(java.time.Instant.now().toString());
            digestRequestJobCache.put(technicalId, job);
            return;
        }

        // Create DigestDataRecord
        String dataRecordId = "data-" + digestDataRecordIdCounter.getAndIncrement();
        DigestDataRecord dataRecord = new DigestDataRecord();
        dataRecord.setJobTechnicalId(technicalId);
        dataRecord.setApiEndpoint(apiUrl);
        dataRecord.setResponseData(apiResponse);
        dataRecord.setFetchedAt(java.time.Instant.now().toString());
        digestDataRecordCache.put(dataRecordId, dataRecord);
        log.info("Created DigestDataRecord {} for job {}", dataRecordId, technicalId);

        // Aggregate data into email content (simple HTML)
        String emailContent = "<html><body><h3>Petstore Digest</h3><pre>" + apiResponse + "</pre></body></html>";

        // Create DigestEmailRecord
        String emailRecordId = "email-" + digestEmailRecordIdCounter.getAndIncrement();
        DigestEmailRecord emailRecord = new DigestEmailRecord();
        emailRecord.setJobTechnicalId(technicalId);
        emailRecord.setEmailContent(emailContent);
        emailRecord.setEmailStatus("PENDING");
        digestEmailRecordCache.put(emailRecordId, emailRecord);
        log.info("Created DigestEmailRecord {} for job {}", emailRecordId, technicalId);

        // Send email (simulate)
        boolean emailSent = sendEmail(job.getUserEmail(), "Your Petstore Digest", emailContent);
        emailRecord.setEmailStatus(emailSent ? "SENT" : "FAILED");
        emailRecord.setEmailSentAt(java.time.Instant.now().toString());
        digestEmailRecordCache.put(emailRecordId, emailRecord);

        // Update job status
        job.setStatus(emailSent ? "COMPLETED" : "FAILED");
        job.setCompletedAt(java.time.Instant.now().toString());
        digestRequestJobCache.put(technicalId, job);

        log.info("Completed processing DigestRequestJob {} with status {}", technicalId, job.getStatus());
    }

    // Simulated email sending method
    private boolean sendEmail(String to, String subject, String content) {
        log.info("Sending email to {} with subject {}", to, subject);
        // In real implementation, integrate with JavaMailSender or other email service
        // Here we simulate success
        return true;
    }
}