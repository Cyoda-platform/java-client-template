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
import com.java_template.application.entity.DigestEmail;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for entities
    private final ConcurrentHashMap<String, DigestRequest> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestData> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestEmail> digestEmailCache = new ConcurrentHashMap<>();
    private final AtomicLong digestEmailIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/digestRequests - create DigestRequest
    @PostMapping("/digestRequests")
    public ResponseEntity<Map<String, String>> createDigestRequest(@RequestBody DigestRequest request) {
        // Validate required fields
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            log.error("Email is missing or blank.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required and cannot be blank."));
        }
        // Generate technical ID
        String technicalId = String.valueOf(digestRequestIdCounter.getAndIncrement());
        digestRequestCache.put(technicalId, request);
        log.info("DigestRequest created with ID: {}", technicalId);

        // Trigger processing
        processDigestRequest(technicalId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/digestRequests/{id} - retrieve DigestRequest by technicalId
    @GetMapping("/digestRequests/{id}")
    public ResponseEntity<?> getDigestRequest(@PathVariable String id) {
        DigestRequest request = digestRequestCache.get(id);
        if (request == null) {
            log.error("DigestRequest with ID {} not found.", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestRequest not found."));
        }
        return ResponseEntity.ok(request);
    }

    // GET /prototype/digestData/{id} - retrieve DigestData by technicalId
    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable String id) {
        DigestData data = digestDataCache.get(id);
        if (data == null) {
            log.error("DigestData with ID {} not found.", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestData not found."));
        }
        return ResponseEntity.ok(data);
    }

    // GET /prototype/digestEmail/{id} - retrieve DigestEmail by technicalId
    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<?> getDigestEmail(@PathVariable String id) {
        DigestEmail email = digestEmailCache.get(id);
        if (email == null) {
            log.error("DigestEmail with ID {} not found.", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestEmail not found."));
        }
        return ResponseEntity.ok(email);
    }

    // processDigestRequest: validate, call external API, save DigestData, trigger processDigestData
    private void processDigestRequest(String digestRequestId, DigestRequest request) {
        log.info("Processing DigestRequest with ID: {}", digestRequestId);

        // Validate email format (basic)
        if (!request.getEmail().matches("^[\\w-.]+@[\\w-]+\\.[a-z]{2,}$")) {
            log.error("Invalid email format for DigestRequest ID: {}", digestRequestId);
            return;
        }

        // Determine external API endpoint and parameters
        String endpoint = "/pet/findByStatus";
        String params = "status=available";

        if (request.getRequestPayload() != null && !request.getRequestPayload().isBlank()) {
            // For simplicity, assume requestPayload contains JSON with "endpoint" and "params" fields
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var jsonNode = mapper.readTree(request.getRequestPayload());
                if (jsonNode.has("endpoint")) {
                    endpoint = jsonNode.get("endpoint").asText();
                }
                if (jsonNode.has("params")) {
                    var paramsNode = jsonNode.get("params");
                    List<String> paramPairs = new ArrayList<>();
                    paramsNode.fieldNames().forEachRemaining(field -> {
                        paramPairs.add(field + "=" + paramsNode.get(field).asText());
                    });
                    params = String.join("&", paramPairs);
                }
            } catch (Exception ex) {
                log.error("Failed to parse requestPayload for DigestRequest ID {}: {}", digestRequestId, ex.getMessage());
            }
        }

        String url = "https://petstore.swagger.io/v2" + endpoint + "?" + params;

        try {
            String responseBody = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class).getBody();

            // Save DigestData entity
            DigestData digestData = new DigestData();
            digestData.setDigestRequestId(digestRequestId);
            digestData.setRetrievedData(responseBody);
            digestData.setFormatType("html"); // as per requirement, output in HTML format

            String digestDataId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestDataCache.put(digestDataId, digestData);

            log.info("DigestData saved with ID: {} for DigestRequest ID: {}", digestDataId, digestRequestId);

            // Trigger processDigestData
            processDigestData(digestDataId, digestData);

        } catch (Exception e) {
            log.error("External API call failed for DigestRequest ID {}: {}", digestRequestId, e.getMessage());
        }
    }

    // processDigestData: compile data into HTML digest, save DigestEmail, trigger processDigestEmail
    private void processDigestData(String digestDataId, DigestData digestData) {
        log.info("Processing DigestData with ID: {}", digestDataId);

        // Compile digest - here we assume the retrievedData is JSON and convert it to simple HTML
        String compiledHtml = "<html><body><h1>Digest Data</h1><pre>" + digestData.getRetrievedData() + "</pre></body></html>";

        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(digestData.getDigestRequestId());
        digestEmail.setEmailContent(compiledHtml);
        digestEmail.setStatus("PENDING");

        String digestEmailId = String.valueOf(digestEmailIdCounter.getAndIncrement());
        digestEmailCache.put(digestEmailId, digestEmail);

        log.info("DigestEmail saved with ID: {} for DigestRequest ID: {}", digestEmailId, digestData.getDigestRequestId());

        // Trigger processDigestEmail
        processDigestEmail(digestEmailId, digestEmail);
    }

    // processDigestEmail: send email and update status
    private void processDigestEmail(String digestEmailId, DigestEmail digestEmail) {
        log.info("Processing DigestEmail with ID: {}", digestEmailId);

        // For prototype, simulate sending email by logging
        String recipient = null;
        // Retrieve email from DigestRequest cache
        for (Map.Entry<String, DigestRequest> entry : digestRequestCache.entrySet()) {
            if (entry.getKey().equals(digestEmail.getDigestRequestId())) {
                recipient = entry.getValue().getEmail();
                break;
            }
        }
        if (recipient == null) {
            log.error("Recipient email not found for DigestEmail ID: {}", digestEmailId);
            digestEmail.setStatus("FAILED");
            digestEmailCache.put(digestEmailId, digestEmail);
            return;
        }

        // Simulate email sending
        try {
            log.info("Sending digest email to: {}", recipient);
            log.info("Email content: {}", digestEmail.getEmailContent());
            // Simulate success
            digestEmail.setStatus("SENT");
            digestEmailCache.put(digestEmailId, digestEmail);
            log.info("DigestEmail ID: {} sent successfully.", digestEmailId);
        } catch (Exception e) {
            log.error("Failed to send DigestEmail ID: {}: {}", digestEmailId, e.getMessage());
            digestEmail.setStatus("FAILED");
            digestEmailCache.put(digestEmailId, digestEmail);
        }
    }
}