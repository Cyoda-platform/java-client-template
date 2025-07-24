package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.time.Instant;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, com.java_template.application.entity.DigestRequestJob> digestRequestJobCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.ExternalApiData> externalApiDataCache = new ConcurrentHashMap<>();
    private final AtomicLong externalApiDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.DigestEmail> digestEmailCache = new ConcurrentHashMap<>();
    private final AtomicLong digestEmailIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/digestRequestJob
    @PostMapping("/digestRequestJob")
    public ResponseEntity<Map<String, String>> createDigestRequestJob(@RequestBody com.java_template.application.entity.DigestRequestJob job) {
        log.info("Received request to create DigestRequestJob");

        if (job == null) {
            log.error("DigestRequestJob payload is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is required"));
        }

        // Set initial status and createdAt if missing
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(Instant.now());
        }

        if (!job.isValid()) {
            log.error("DigestRequestJob validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid DigestRequestJob fields"));
        }

        String technicalId = String.valueOf(digestRequestJobIdCounter.getAndIncrement());
        digestRequestJobCache.put(technicalId, job);

        log.info("DigestRequestJob created with ID: {}", technicalId);

        processDigestRequestJob(technicalId, job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/digestRequestJob/{id}
    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<com.java_template.application.entity.DigestRequestJob> getDigestRequestJob(@PathVariable("id") String id) {
        com.java_template.application.entity.DigestRequestJob job = digestRequestJobCache.get(id);
        if (job == null) {
            log.error("DigestRequestJob not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/externalApiData/{id}
    @GetMapping("/externalApiData/{id}")
    public ResponseEntity<com.java_template.application.entity.ExternalApiData> getExternalApiData(@PathVariable("id") String id) {
        com.java_template.application.entity.ExternalApiData data = externalApiDataCache.get(id);
        if (data == null) {
            log.error("ExternalApiData not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(data);
    }

    // GET /prototype/digestEmail/{id}
    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<com.java_template.application.entity.DigestEmail> getDigestEmail(@PathVariable("id") String id) {
        com.java_template.application.entity.DigestEmail email = digestEmailCache.get(id);
        if (email == null) {
            log.error("DigestEmail not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(email);
    }

    private void processDigestRequestJob(String technicalId, com.java_template.application.entity.DigestRequestJob job) {
        log.info("Processing DigestRequestJob with ID: {}", technicalId);

        // Step 1: Validation (already done on input, but double-check)
        if (!job.isValid()) {
            log.error("DigestRequestJob {} validation failed during processing", technicalId);
            job.setStatus("FAILED");
            digestRequestJobCache.put(technicalId, job);
            return;
        }

        job.setStatus("PROCESSING");
        digestRequestJobCache.put(technicalId, job);

        // Step 2: Call external API
        // Determine the endpoint dynamically or use default
        String endpoint = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
        if (job.getRequestMetadata() != null && !job.getRequestMetadata().isBlank()) {
            // For example, if requestMetadata contains a status parameter, use it
            // Simple parse logic: expecting requestMetadata like "status=available"
            String metadata = job.getRequestMetadata().trim();
            if (metadata.startsWith("status=")) {
                String statusValue = metadata.substring(7);
                endpoint = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusValue;
            }
        }

        com.java_template.application.entity.ExternalApiData apiData = new com.java_template.application.entity.ExternalApiData();
        apiData.setJobTechnicalId(technicalId);
        apiData.setApiEndpoint(endpoint);
        apiData.setFetchedAt(Instant.now());

        try {
            log.info("Calling external API at {}", endpoint);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String response = restTemplate.getForObject(endpoint, String.class);
            apiData.setResponseData(response);
            if (!apiData.isValid()) {
                throw new RuntimeException("ExternalApiData validation failed");
            }
            String apiDataId = String.valueOf(externalApiDataIdCounter.getAndIncrement());
            externalApiDataCache.put(apiDataId, apiData);
            log.info("ExternalApiData saved with ID: {}", apiDataId);

            // Step 3: Compile digest email content
            String emailContent = compileDigestContent(response);

            com.java_template.application.entity.DigestEmail digestEmail = new com.java_template.application.entity.DigestEmail();
            digestEmail.setJobTechnicalId(technicalId);
            digestEmail.setEmailContent(emailContent);
            digestEmail.setSentAt(null);
            digestEmail.setDeliveryStatus("PENDING");

            if (!digestEmail.isValid()) {
                throw new RuntimeException("DigestEmail validation failed");
            }

            String digestEmailId = String.valueOf(digestEmailIdCounter.getAndIncrement());
            digestEmailCache.put(digestEmailId, digestEmail);
            log.info("DigestEmail saved with ID: {}", digestEmailId);

            // Step 4: Send email
            boolean emailSent = sendEmail(job.getEmail(), emailContent);
            if (emailSent) {
                digestEmail.setSentAt(Instant.now());
                digestEmail.setDeliveryStatus("SENT");
                log.info("Email sent successfully to {}", job.getEmail());
            } else {
                digestEmail.setDeliveryStatus("FAILED");
                log.error("Failed to send email to {}", job.getEmail());
            }
            digestEmailCache.put(digestEmailId, digestEmail);

            // Step 5: Finalize job status
            job.setStatus(emailSent ? "COMPLETED" : "FAILED");
            digestRequestJobCache.put(technicalId, job);

        } catch (Exception e) {
            log.error("Error processing DigestRequestJob {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            digestRequestJobCache.put(technicalId, job);
        }
    }

    private String compileDigestContent(String apiResponse) {
        // For prototype, wrap response in simple HTML
        if (apiResponse == null || apiResponse.isBlank()) {
            return "<html><body><p>No data available for digest.</p></body></html>";
        }
        return "<html><body><h3>Your Digest</h3><pre>" + apiResponse + "</pre></body></html>";
    }

    private boolean sendEmail(String recipientEmail, String content) {
        // Prototype email sending logic - just log and simulate success
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.error("Recipient email is blank, cannot send email");
            return false;
        }
        log.info("Simulating sending email to: {}", recipientEmail);
        // In real implementation, integrate with email service here
        return true;
    }
}