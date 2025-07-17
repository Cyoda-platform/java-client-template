```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype/digest")
@RequiredArgsConstructor
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // == POST /digest/request ==
    @PostMapping("/request")
    public ResponseEntity<DigestRequestResponse> submitDigestRequest(@Valid @RequestBody DigestRequest request) {
        log.info("Received digest request for email={} with metadata={}", request.getEmail(), request.getMetadata());

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Track job as processing initially
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));

        // Fire-and-forget digest processing asynchronously
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request));

        return ResponseEntity.accepted()
                .body(new DigestRequestResponse(jobId, "accepted"));
    }

    // == GET /digest/status/{requestId} ==
    @GetMapping("/status/{requestId}")
    public ResponseEntity<JobStatusResponse> getDigestStatus(@PathVariable("requestId") String requestId) {
        JobStatus status = entityJobs.get(requestId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RequestId not found");
        }

        JobStatusResponse response = new JobStatusResponse(
                requestId,
                status.getEmail(),
                status.getStatus(),
                status.getSentAt(),
                status.getDigestSummary()
        );

        return ResponseEntity.ok(response);
    }

    // == Core processing logic (async) ==
    @Async
    protected void processDigestRequest(String jobId, DigestRequest request) {
        log.info("Processing digest jobId={} for email={}", jobId, request.getEmail());

        try {
            // Step 1: Determine API endpoint and parameters based on metadata or defaults
            String digestType = request.getMetadata() != null ? request.getMetadata().getDigestType() : null;
            Map<String, String> params = request.getMetadata() != null ? request.getMetadata().getParameters() : null;

            // Default endpoint & params if none provided
            String apiUrl;
            if ("petStatusDigest".equalsIgnoreCase(digestType)) {
                // Use petstore endpoint /pet/findByStatus?status=available or from params
                String statusParam = (params != null && StringUtils.hasText(params.get("status"))) ?
                        params.get("status") : "available";
                apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam;
            } else {
                // TODO: Extend logic for other digestTypes
                apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            }

            log.info("Fetching external data from {}", apiUrl);
            String rawJson = restTemplate.getForObject(URI.create(apiUrl), String.class);

            if (rawJson == null) {
                throw new IllegalStateException("Empty response from external API");
            }

            JsonNode dataNode = objectMapper.readTree(rawJson);

            // Step 2: Compile digest (simple HTML string)
            String digestHtml = compileDigestHtml(dataNode);

            // Step 3: Send email (mock implementation)
            sendEmail(request.getEmail(), "Your Pet Status Digest", digestHtml);

            // Update job status as completed
            entityJobs.put(jobId, new JobStatus(
                    "completed",
                    Instant.now(),
                    request.getEmail(),
                    "Sent pet status digest with " + (dataNode.isArray() ? dataNode.size() : 1) + " entries"
            ));

            log.info("Digest jobId={} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Error processing digest jobId={}: {}", jobId, e.toString());
            entityJobs.put(jobId, new JobStatus(
                    "failed",
                    Instant.now(),
                    request.getEmail(),
                    "Error: " + e.getMessage()
            ));
        }
    }

    // Simple HTML digest compilation from JsonNode (demo)
    private String compileDigestHtml(JsonNode dataNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h1>Pet Status Digest</h1>");
        sb.append("<ul>");
        if (dataNode.isArray()) {
            for (JsonNode pet : dataNode) {
                String name = pet.has("name") ? pet.get("name").asText() : "Unnamed";
                String status = pet.has("status") ? pet.get("status").asText() : "unknown";
                sb.append("<li>").append("Pet: ").append(name).append(", Status: ").append(status).append("</li>");
            }
        } else {
            sb.append("<li>Pet data unavailable</li>");
        }
        sb.append("</ul>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // Mock email sending implementation - TODO replace with real email service integration
    private void sendEmail(String toEmail, String subject, String htmlContent) {
        log.info("Mock sending email to={} subject={}", toEmail, subject);
        // TODO: Integrate with real email service (SMTP, SES, SendGrid, etc.)
    }

    // == Exception handler for validation errors and others ==
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.toString());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error"));
    }

    // == DTOs ==

    @Data
    public static class DigestRequest {
        @NotBlank
        @Email
        private String email;

        private Metadata metadata;

        @Data
        public static class Metadata {
            private String digestType;
            private Map<String, String> parameters;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class DigestRequestResponse {
        private final String requestId;
        private final String status;
    }

    @Data
    public static class JobStatusResponse {
        private final String requestId;
        private final String email;
        private final String status;
        private final Instant sentAt;
        private final String digestSummary;
    }

    @Getter
    public static class JobStatus {
        private final String status;
        private final Instant sentAt;
        private final String email;
        private final String digestSummary;

        // Constructor for initial job with minimal data
        public JobStatus(String status, Instant sentAt) {
            this.status = status;
            this.sentAt = sentAt;
            this.email = null;
            this.digestSummary = null;
        }

        // Constructor for finished job with full info
        public JobStatus(String status, Instant sentAt, String email, String digestSummary) {
            this.status = status;
            this.sentAt = sentAt;
            this.email = email;
            this.digestSummary = digestSummary;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}
```