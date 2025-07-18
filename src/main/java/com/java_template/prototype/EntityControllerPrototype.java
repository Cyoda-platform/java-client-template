```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping(path = "/prototype/digest")
@RequiredArgsConstructor
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String DEFAULT_ENDPOINT = "/pet/findByStatus";
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage of jobs (requestId -> JobStatus)
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    @PostMapping(path = "/request", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestRequestResponse submitDigestRequest(@RequestBody DigestRequest request) {
        logger.info("Received digest request for email: {}", request.getEmail());

        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be blank");
        }

        // Log event with timestamp and metadata (in this prototype just keep in memory)
        Instant requestedAt = Instant.now();
        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));

        // Fire-and-forget processing asynchronously
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request, requestedAt));

        return new DigestRequestResponse("accepted", "Digest request received and processing started", jobId);
    }

    @GetMapping(path = "/status/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JobStatus getDigestStatus(@PathVariable("requestId") String requestId) {
        JobStatus status = entityJobs.get(requestId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request ID not found");
        }
        return status;
    }

    @Async  // Marking for clarity, though CompletableFuture already async
    void processDigestRequest(String jobId, DigestRequest request, Instant requestedAt) {
        logger.info("Started processing digest for jobId={}", jobId);

        try {
            // Determine endpoint and parameters
            String endpoint = DEFAULT_ENDPOINT;
            Map<String, String> params = Map.of("status", "available"); // default param

            if (request.getMetadata() != null) {
                if (StringUtils.hasText(request.getMetadata().getEndpoint())) {
                    endpoint = request.getMetadata().getEndpoint();
                }
                if (request.getMetadata().getParameters() != null && !request.getMetadata().getParameters().isEmpty()) {
                    params = request.getMetadata().getParameters();
                }
            }

            // Build full URI with query parameters
            StringBuilder uriBuilder = new StringBuilder(PETSTORE_BASE_URL).append(endpoint);
            if (!params.isEmpty()) {
                uriBuilder.append("?");
                params.forEach((k, v) -> uriBuilder.append(k).append("=").append(v).append("&"));
                uriBuilder.setLength(uriBuilder.length() - 1); // remove trailing '&'
            }
            URI uri = URI.create(uriBuilder.toString());

            logger.info("Calling external API: {}", uri);

            // Call external API
            String rawJson = restTemplate.getForObject(uri, String.class);
            if (rawJson == null) {
                throw new IllegalStateException("External API returned empty response");
            }
            JsonNode dataNode = objectMapper.readTree(rawJson);

            // Compile digest as HTML string (simple prototype)
            String digestHtml = compileDigestHtml(dataNode);

            // Send email (TODO: replace with real email sending)
            sendEmail(request.getEmail(), digestHtml);

            // Update job status
            entityJobs.put(jobId, new JobStatus("completed", requestedAt, Instant.now(), request.getEmail(), digestHtml));
            logger.info("Completed processing digest for jobId={}", jobId);

        } catch (Exception e) {
            logger.error("Error processing digest for jobId=" + jobId, e);
            entityJobs.put(jobId, new JobStatus("failed", requestedAt, Instant.now(), e.getMessage()));
        }
    }

    private String compileDigestHtml(JsonNode dataNode) {
        // Simple HTML formatting of JSON data for prototype
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>Petstore Digest</h2>");
        html.append("<pre>").append(dataNode.toPrettyString()).append("</pre>");
        html.append("</body></html>");
        return html.toString();
    }

    private void sendEmail(String toEmail, String htmlContent) {
        // TODO: Implement real email sending logic.
        // For prototype just log it.
        logger.info("Sending email to: {}\nContent preview:\n{}", toEmail, htmlContent.substring(0, Math.min(200, htmlContent.length())) + "...");
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        HttpStatus status = ex.getStatusCode();
        return new ErrorResponse(status.toString(), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneralException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
    }

    // --- DTOs and Models ---

    @Data
    public static class DigestRequest {
        @NotBlank @Email
        private String email;
        private Metadata metadata;

        @Data
        public static class Metadata {
            private String endpoint;
            private Map<String, String> parameters;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class DigestRequestResponse {
        private final String status;
        private final String message;
        private final String requestId;
    }

    @Data
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
        private Instant completedAt;
        private String email;
        private String digestSummary;
        private String error;

        // Processing / initial state constructor
        public JobStatus(String status, Instant requestedAt) {
            this.status = status;
            this.requestedAt = requestedAt;
        }

        // Completed state constructor
        public JobStatus(String status, Instant requestedAt, Instant completedAt, String email, String digestSummary) {
            this.status = status;
            this.requestedAt = requestedAt;
            this.completedAt = completedAt;
            this.email = email;
            this.digestSummary = digestSummary;
        }

        // Failed state constructor
        public JobStatus(String status, Instant requestedAt, Instant completedAt, String error) {
            this.status = status;
            this.requestedAt = requestedAt;
            this.completedAt = completedAt;
            this.error = error;
        }
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}
```