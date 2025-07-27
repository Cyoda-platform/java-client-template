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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;

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

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/digestRequests
    @PostMapping("/digestRequests")
    public ResponseEntity<Map<String, String>> createDigestRequest(@RequestBody DigestRequest request) {
        if (request == null) {
            log.error("Received null DigestRequest");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is required"));
        }
        // Validate required fields
        if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "userEmail is required"));
        }
        if (request.getExternalApiEndpoint() == null || request.getExternalApiEndpoint().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "externalApiEndpoint is required"));
        }

        // Assign technicalId
        String technicalId = String.valueOf(digestRequestIdCounter.getAndIncrement());
        request.setRequestTimestamp(Instant.now());
        request.setStatus("PENDING");

        digestRequestCache.put(technicalId, request);
        log.info("Created DigestRequest with technicalId: {}", technicalId);

        // Trigger processing in a new thread to simulate async event-driven processing
        new Thread(() -> processDigestRequest(technicalId, request)).start();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/digestRequests/{id}
    @GetMapping("/digestRequests/{id}")
    public ResponseEntity<?> getDigestRequest(@PathVariable String id) {
        DigestRequest request = digestRequestCache.get(id);
        if (request == null) {
            log.error("DigestRequest not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestRequest not found"));
        }
        return ResponseEntity.ok(request);
    }

    // GET /prototype/digestData/{id}
    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable String id) {
        DigestData data = digestDataCache.get(id);
        if (data == null) {
            log.error("DigestData not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestData not found"));
        }
        return ResponseEntity.ok(data);
    }

    // GET /prototype/emailDispatch/{id}
    @GetMapping("/emailDispatch/{id}")
    public ResponseEntity<?> getEmailDispatch(@PathVariable String id) {
        EmailDispatch emailDispatch = emailDispatchCache.get(id);
        if (emailDispatch == null) {
            log.error("EmailDispatch not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "EmailDispatch not found"));
        }
        return ResponseEntity.ok(emailDispatch);
    }

    // Process methods
    private void processDigestRequest(String technicalId, DigestRequest request) {
        log.info("Processing DigestRequest with id: {}", technicalId);
        // Validate again for safety
        if (!request.isValid()) {
            log.error("DigestRequest {} validation failed", technicalId);
            request.setStatus("FAILED");
            digestRequestCache.put(technicalId, request);
            return;
        }
        request.setStatus("PROCESSING");
        digestRequestCache.put(technicalId, request);

        // Retrieve data from external API (Petstore Swagger API)
        try {
            String baseUrl = "https://petstore.swagger.io/v2";
            String endpoint = request.getExternalApiEndpoint();
            String url = baseUrl + endpoint;

            log.info("Calling external API: {}", url);
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String apiResponse = response.getBody();

            if (apiResponse == null || apiResponse.isBlank()) {
                log.error("Empty response from external API for DigestRequest {}", technicalId);
                request.setStatus("FAILED");
                digestRequestCache.put(technicalId, request);
                return;
            }

            // Persist DigestData
            DigestData digestData = new DigestData();
            String digestDataId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestData.setDigestRequestId(technicalId);
            digestData.setRetrievedData(apiResponse);
            digestData.setProcessedTimestamp(Instant.now());
            digestDataCache.put(digestDataId, digestData);
            log.info("Persisted DigestData with id: {}", digestDataId);

            // Update DigestRequest status
            request.setStatus("COMPLETED");
            digestRequestCache.put(technicalId, request);

            // Trigger email dispatch process
            processEmailDispatch(technicalId, request, digestData);

        } catch (Exception e) {
            log.error("Error while retrieving data for DigestRequest {}: {}", technicalId, e.getMessage());
            request.setStatus("FAILED");
            digestRequestCache.put(technicalId, request);
        }
    }

    private void processEmailDispatch(String digestRequestId, DigestRequest request, DigestData digestData) {
        log.info("Processing EmailDispatch for DigestRequest id: {}", digestRequestId);

        EmailDispatch emailDispatch = new EmailDispatch();
        String emailDispatchId = String.valueOf(emailDispatchIdCounter.getAndIncrement());
        emailDispatch.setDigestRequestId(digestRequestId);

        // Compose email content (simple HTML format)
        String emailContent = "<html><body><h3>Digest Data</h3><pre>" + digestData.getRetrievedData() + "</pre></body></html>";

        emailDispatch.setEmailContent(emailContent);
        emailDispatch.setDispatchTimestamp(Instant.now());
        emailDispatch.setStatus("PENDING");

        emailDispatchCache.put(emailDispatchId, emailDispatch);

        // Simulate sending email
        try {
            // Here you would integrate with real email service provider
            log.info("Sending email to {}", request.getUserEmail());
            // Simulate delay
            Thread.sleep(500);

            emailDispatch.setStatus("SENT");
            emailDispatchCache.put(emailDispatchId, emailDispatch);
            log.info("Email sent successfully for DigestRequest id: {}", digestRequestId);

        } catch (Exception e) {
            log.error("Failed to send email for DigestRequest {}: {}", digestRequestId, e.getMessage());
            emailDispatch.setStatus("FAILED");
            emailDispatchCache.put(emailDispatchId, emailDispatch);
        }
    }
}