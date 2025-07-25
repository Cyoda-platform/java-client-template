package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
import java.time.Instant;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, DigestRequest> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestData> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, EmailDispatch> emailDispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    // POST /prototype/digestRequest - Create DigestRequest
    @PostMapping("/digestRequest")
    public ResponseEntity<Map<String, String>> createDigestRequest(@RequestBody DigestRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            log.error("DigestRequest creation failed: email is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required"));
        }
        // Initialize fields
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        request.setCreatedAt(Instant.now());
        request.setStatus("PENDING");
        digestRequestCache.put(id, request);
        log.info("DigestRequest created with ID: {}", id);

        // Trigger processing
        processDigestRequest(id, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    // GET /prototype/digestRequest/{id} - Retrieve DigestRequest
    @GetMapping("/digestRequest/{id}")
    public ResponseEntity<Object> getDigestRequest(@PathVariable String id) {
        DigestRequest request = digestRequestCache.get(id);
        if (request == null) {
            log.error("DigestRequest not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestRequest not found"));
        }
        return ResponseEntity.ok(request);
    }

    // GET /prototype/digestData/{id} - Retrieve DigestData
    @GetMapping("/digestData/{id}")
    public ResponseEntity<Object> getDigestData(@PathVariable String id) {
        DigestData data = digestDataCache.get(id);
        if (data == null) {
            log.error("DigestData not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestData not found"));
        }
        return ResponseEntity.ok(data);
    }

    // GET /prototype/emailDispatch/{id} - Retrieve EmailDispatch
    @GetMapping("/emailDispatch/{id}")
    public ResponseEntity<Object> getEmailDispatch(@PathVariable String id) {
        EmailDispatch email = emailDispatchCache.get(id);
        if (email == null) {
            log.error("EmailDispatch not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "EmailDispatch not found"));
        }
        return ResponseEntity.ok(email);
    }

    // Business logic: processDigestRequest
    private void processDigestRequest(String id, DigestRequest entity) {
        log.info("Processing DigestRequest with ID: {}", id);
        try {
            // Validate email and metadata
            if (entity.getEmail() == null || entity.getEmail().isBlank()) {
                log.error("DigestRequest ID {} validation failed: email is blank", id);
                entity.setStatus("FAILED");
                digestRequestCache.put(id, entity);
                return;
            }
            entity.setStatus("PROCESSING");
            digestRequestCache.put(id, entity);

            // Create DigestData entity
            DigestData digestData = new DigestData();
            String digestDataId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestData.setDigestRequestId(id);
            digestData.setCreatedAt(Instant.now());
            digestData.setStatus("PENDING");
            digestDataCache.put(digestDataId, digestData);

            // Trigger processing of DigestData
            processDigestData(digestDataId, digestData);

        } catch (Exception e) {
            log.error("Exception in processDigestRequest for ID {}: {}", id, e.getMessage(), e);
            entity.setStatus("FAILED");
            digestRequestCache.put(id, entity);
        }
    }

    // Business logic: processDigestData
    private void processDigestData(String id, DigestData entity) {
        log.info("Processing DigestData with ID: {}", id);
        try {
            entity.setStatus("PROCESSING");
            digestDataCache.put(id, entity);

            // Prepare external API call to Petstore
            RestTemplate restTemplate = new RestTemplate();
            String apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            // If requestMetadata contains parameters, could parse and adjust apiUrl here - simplified to default

            // Call external API
            String apiResponse = restTemplate.getForObject(apiUrl, String.class);
            if (apiResponse == null || apiResponse.isBlank()) {
                log.error("Empty response from Petstore API for DigestData ID {}", id);
                entity.setStatus("FAILED");
                digestDataCache.put(id, entity);
                return;
            }

            entity.setApiData(apiResponse);
            entity.setStatus("SUCCESS");
            digestDataCache.put(id, entity);

            // Create EmailDispatch entity
            EmailDispatch emailDispatch = new EmailDispatch();
            String emailDispatchId = String.valueOf(emailDispatchIdCounter.getAndIncrement());
            emailDispatch.setDigestRequestId(entity.getDigestRequestId());
            emailDispatch.setEmailContent(""); // will be set during email processing
            emailDispatch.setStatus("PENDING");
            emailDispatchCache.put(emailDispatchId, emailDispatch);

            // Trigger processing email dispatch
            processEmailDispatch(emailDispatchId, emailDispatch);

        } catch (Exception e) {
            log.error("Exception in processDigestData for ID {}: {}", id, e.getMessage(), e);
            entity.setStatus("FAILED");
            digestDataCache.put(id, entity);
        }
    }

    // Business logic: processEmailDispatch
    private void processEmailDispatch(String id, EmailDispatch entity) {
        log.info("Processing EmailDispatch with ID: {}", id);
        try {
            entity.setStatus("PROCESSING");
            emailDispatchCache.put(id, entity);

            // Retrieve associated DigestRequest to get email
            DigestRequest digestRequest = digestRequestCache.get(entity.getDigestRequestId());
            if (digestRequest == null) {
                log.error("Associated DigestRequest not found for EmailDispatch ID: {}", id);
                entity.setStatus("FAILED");
                emailDispatchCache.put(id, entity);
                return;
            }

            // Retrieve associated DigestData to get apiData
            Optional<DigestData> maybeDigestData = digestDataCache.values().stream()
                    .filter(d -> d.getDigestRequestId().equals(entity.getDigestRequestId()) && "SUCCESS".equals(d.getStatus()))
                    .findFirst();

            if (maybeDigestData.isEmpty()) {
                log.error("Associated successful DigestData not found for EmailDispatch ID: {}", id);
                entity.setStatus("FAILED");
                emailDispatchCache.put(id, entity);
                return;
            }

            DigestData digestData = maybeDigestData.get();

            // Format email content (simple plain text)
            String emailContent = "Digest for request ID: " + entity.getDigestRequestId() + "\n\nData:\n" + digestData.getApiData();
            entity.setEmailContent(emailContent);

            // Send email - simulate sending by logging
            log.info("Sending email to {} with digest content length {}", digestRequest.getEmail(), emailContent.length());
            // In real implementation, integrate with mail service here

            entity.setStatus("SENT");
            entity.setSentAt(Instant.now());
            emailDispatchCache.put(id, entity);

            // Update DigestRequest status to COMPLETED
            digestRequest.setStatus("COMPLETED");
            digestRequestCache.put(entity.getDigestRequestId(), digestRequest);

        } catch (Exception e) {
            log.error("Exception in processEmailDispatch for ID {}: {}", id, e.getMessage(), e);
            entity.setStatus("FAILED");
            emailDispatchCache.put(id, entity);
        }
    }
}