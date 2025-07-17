```java
package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatchLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prototype controller simulating event-driven entity processing.
 * Stores entities in thread-safe local caches and triggers processEntity methods after persistence.
 */
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // ======== DigestRequest Cache and ID generator ========
    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    // ======== DigestData Cache and ID generator ========
    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    // ======== EmailDispatchLog Cache and ID generator ========
    private final ConcurrentHashMap<String, List<EmailDispatchLog>> emailDispatchLogCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchLogIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // ======== POST /digest-requests ========
    @PostMapping("/digest-requests")
    public CreateDigestResponse createOrUpdateDigestRequest(@Valid @RequestBody DigestRequestInput input) {
        logger.info("Received DigestRequest create/update request for email={}", input.getEmail());

        // Create new DigestRequest entity
        DigestRequest req = new DigestRequest();
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        req.setId(id);
        req.setEmail(input.getEmail());
        req.setMetadata(input.getMetadata() != null ? input.getMetadata() : new HashMap<>());
        req.setRequestedEndpoint(
                input.getRequestedEndpoint() != null && !input.getRequestedEndpoint().isBlank()
                        ? input.getRequestedEndpoint() : "/pet/findByStatus");
        req.setRequestedParameters(
                input.getRequestedParameters() != null ? input.getRequestedParameters() : Map.of("status", "available"));
        req.setDigestFormat(input.getDigestFormat() != null ? input.getDigestFormat() : DigestRequest.DigestFormat.HTML);
        req.setStatus(DigestRequest.Status.RECEIVED);
        req.setCreatedAt(Instant.now());
        req.setUpdatedAt(Instant.now());

        // Save entity to cache
        digestRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(req);
        logger.info("Saved DigestRequest id={} to cache", id);

        // Trigger event processing
        processDigestRequest(req);

        return new CreateDigestResponse(id, req.getStatus().toString());
    }

    // ======== GET /digest-requests/{id} ========
    @GetMapping("/digest-requests/{id}")
    public DigestRequest getDigestRequest(@PathVariable String id) {
        Optional<DigestRequest> found = findDigestRequestById(id);
        if (found.isEmpty()) {
            logger.error("DigestRequest id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        return found.get();
    }

    // ======== GET /digest-requests/{id}/digest-data ========
    @GetMapping("/digest-requests/{id}/digest-data")
    public DigestDataResponse getDigestData(@PathVariable String id) {
        Optional<DigestData> found = findDigestDataByRequestId(id);
        if (found.isEmpty()) {
            logger.error("DigestData for DigestRequest id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
        }
        return new DigestDataResponse(found.get().getRetrievedData());
    }

    // ======== Helper: Find DigestRequest by ID ========
    private Optional<DigestRequest> findDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        return list.stream().filter(r -> id.equals(r.getId())).findFirst();
    }

    // ======== Helper: Find DigestData by DigestRequest ID ========
    private Optional<DigestData> findDigestDataByRequestId(String digestRequestId) {
        List<DigestData> list = digestDataCache.getOrDefault("entities", Collections.emptyList());
        return list.stream().filter(d -> digestRequestId.equals(d.getDigestRequestId())).findFirst();
    }

    // ======== Event Processing Simulation ========
    private void processDigestRequest(DigestRequest req) {
        logger.info("Start processing DigestRequest id={}", req.getId());
        try {
            // Update status to PROCESSING
            req.setStatus(DigestRequest.Status.PROCESSING);
            req.setUpdatedAt(Instant.now());

            // Make external API call to petstore.swagger.io
            String url = "https://petstore.swagger.io/v2" + req.getRequestedEndpoint();

            // Build query params from requestedParameters
            StringBuilder urlBuilder = new StringBuilder(url);
            if (req.getRequestedParameters() != null && !req.getRequestedParameters().isEmpty()) {
                urlBuilder.append("?");
                req.getRequestedParameters().forEach((k, v) -> {
                    urlBuilder.append(k).append("=").append(v).append("&");
                });
                urlBuilder.setLength(urlBuilder.length() - 1); // remove trailing &
            }

            logger.info("Fetching data from external API: {}", urlBuilder);
            ResponseEntity<String> response = restTemplate.getForEntity(urlBuilder.toString(), String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("External API call failed with status " + response.getStatusCodeValue());
            }

            String responseBody = response.getBody();

            // Save retrieved data as DigestData entity
            DigestData digestData = new DigestData();
            String digestDataId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestData.setId(digestDataId);
            digestData.setDigestRequestId(req.getId());
            digestData.setRetrievedData(responseBody);
            digestData.setCreatedAt(Instant.now());
            digestDataCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(digestData);
            logger.info("Saved DigestData id={} for DigestRequest id={}", digestDataId, req.getId());

            // Compile digest (for prototype, just reuse retrieved data as-is)
            String compiledDigest = compileDigest(responseBody, req.getDigestFormat());

            // Send email (mocked here)
            EmailDispatchLog emailLog = new EmailDispatchLog();
            String emailLogId = String.valueOf(emailDispatchLogIdCounter.getAndIncrement());
            emailLog.setId(emailLogId);
            emailLog.setDigestRequestId(req.getId());
            emailLog.setEmail(req.getEmail());
            emailLog.setDispatchStatus(EmailDispatchLog.DispatchStatus.PENDING);
            emailLog.setSentAt(null);
            emailLog.setErrorMessage(null);
            emailDispatchLogCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(emailLog);

            // Mock sending email - TODO: Replace with real email sending logic
            boolean emailSent = mockSendEmail(req.getEmail(), compiledDigest, req.getDigestFormat());
            if (emailSent) {
                emailLog.setDispatchStatus(EmailDispatchLog.DispatchStatus.SUCCESS);
                emailLog.setSentAt(Instant.now());
                req.setStatus(DigestRequest.Status.SENT);
                logger.info("Email sent successfully to {}", req.getEmail());
            } else {
                emailLog.setDispatchStatus(EmailDispatchLog.DispatchStatus.FAILED);
                emailLog.setSentAt(Instant.now());
                emailLog.setErrorMessage("Mock email sending failure");
                req.setStatus(DigestRequest.Status.FAILED);
                logger.error("Email sending failed for {}", req.getEmail());
            }

            req.setUpdatedAt(Instant.now());
        } catch (Exception e) {
            logger.error("Error processing DigestRequest id={}: {}", req.getId(), e.getMessage(), e);
            req.setStatus(DigestRequest.Status.FAILED);
            req.setUpdatedAt(Instant.now());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process DigestRequest: " + e.getMessage());
        }
    }

    // ======== Compile digest content based on format ========
    private String compileDigest(String data, DigestRequest.DigestFormat format) {
        // For prototype, just wrap JSON data in minimal HTML or plain text
        switch (format) {
            case HTML:
                return "<html><body><pre>" + escapeHtml(data) + "</pre></body></html>";
            case PLAIN_TEXT:
                return data;
            case ATTACHMENT:
                // For prototype, return base64 encoded string to simulate attachment
                return Base64.getEncoder().encodeToString(data.getBytes());
            default:
                return data;
        }
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // ======== Mock email sending ========
    private boolean mockSendEmail(String toEmail, String content, DigestRequest.DigestFormat format) {
        logger.info("Mock sending email to {} with digest format {}", toEmail, format);
        // TODO: Replace with real email sending implementation
        return true;
    }

    // ======== Input and Response Classes ========

    @Data
    public static class DigestRequestInput {
        @NotBlank
        @Email
        private String email;

        private Map<String, String> metadata;

        private String requestedEndpoint;

        private Map<String, String> requestedParameters;

        private DigestRequest.DigestFormat digestFormat;
    }

    @Data
    public static class CreateDigestResponse {
        private final String id;
        private final String status;
    }

    @Data
    public static class DigestDataResponse {
        private final String retrievedData;
    }

    // ======== Exception Handling ========

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

}
```
