package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/digest")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store to mock persistence of requests and their statuses
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String DEFAULT_ENDPOINT = "/pet/findByStatus";
    private static final Map<String, String> DEFAULT_PARAMETERS = Map.of("status", "available");

    @PostMapping("/request")
    public ResponseEntity<DigestResponse> createDigestRequest(@Valid @RequestBody DigestRequest request) {
        logger.info("Received digest request for email: {}", request.getEmail());

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt, request.getEmail(), request.getMetadata()));

        // Fire-and-forget processing of the digest
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request));

        DigestResponse response = new DigestResponse("success", "Digest request processed and email sent.", jobId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<JobStatus> getDigestStatus(@PathVariable("requestId") String requestId) {
        JobStatus status = entityJobs.get(requestId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request ID not found");
        }
        return ResponseEntity.ok(status);
    }

    private void processDigestRequest(String jobId, DigestRequest request) {
        try {
            logger.info("Start processing digest request with ID: {}", jobId);

            // Determine endpoint and parameters
            String endpoint = DEFAULT_ENDPOINT;
            Map<String, String> parameters = DEFAULT_PARAMETERS;
            if (request.getApiRequest() != null && StringUtils.hasText(request.getApiRequest().getEndpoint())) {
                endpoint = request.getApiRequest().getEndpoint();
                if (request.getApiRequest().getParameters() != null && !request.getApiRequest().getParameters().isEmpty()) {
                    parameters = request.getApiRequest().getParameters();
                }
            }
            logger.info("Using endpoint: {} with parameters: {}", endpoint, parameters);

            // Build full URL for external API call
            StringBuilder urlBuilder = new StringBuilder("https://petstore.swagger.io/v2").append(endpoint);
            if (!parameters.isEmpty()) {
                urlBuilder.append("?");
                parameters.forEach((k, v) -> urlBuilder.append(k).append("=").append(v).append("&"));
                urlBuilder.deleteCharAt(urlBuilder.length() - 1); // Remove trailing &
            }
            String url = urlBuilder.toString();
            logger.info("Calling external API URL: {}", url);

            // Call external API
            String apiResponse = restTemplate.getForObject(url, String.class);

            // Parse JSON response
            JsonNode dataNode = objectMapper.readTree(apiResponse);

            // Compile digest email content (simple HTML/plain text mock)
            String emailContent = compileEmailContent(dataNode, request.getEmailFormat());

            // Send email (mocked)
            sendEmail(request.getEmail(), emailContent, request.getEmailFormat());

            // Update job status to completed
            entityJobs.put(jobId, new JobStatus("completed", Instant.now(), request.getEmail(), request.getMetadata()));

            logger.info("Completed processing digest request with ID: {}", jobId);

        } catch (Exception e) {
            logger.error("Error processing digest request with ID: {}: {}", jobId, e.getMessage(), e);
            entityJobs.put(jobId, new JobStatus("failed", Instant.now(), request.getEmail(), request.getMetadata()));
        }
    }

    private String compileEmailContent(JsonNode dataNode, String format) {
        // TODO: Improve formatting logic. For now, just return pretty-printed JSON as HTML or plain text.
        if ("plain".equalsIgnoreCase(format)) {
            return dataNode.toPrettyString();
        } else if ("attachment".equalsIgnoreCase(format)) {
            // TODO: Handle attachment generation (not implemented here)
            return "Attachment format requested - feature not implemented. Sending plain text instead.\n\n" + dataNode.toPrettyString();
        } else {
            // Default to HTML
            return "<html><body><pre>" + dataNode.toPrettyString() + "</pre></body></html>";
        }
    }

    private void sendEmail(String toEmail, String content, String format) {
        // TODO: Replace this mock with real email sending logic
        logger.info("Sending email to: {} with format: {}", toEmail, format != null ? format : "html");
        logger.info("Email content preview:\n{}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error"));
    }

    // DTOs and status classes

    @Data
    public static class DigestRequest {
        @Email
        @NotBlank
        private String email;

        private Map<String, String> metadata;

        private ApiRequest apiRequest;

        private String emailFormat; // optional: "plain", "html", "attachment"
    }

    @Data
    public static class ApiRequest {
        @NotBlank
        private String endpoint;

        private Map<String, String> parameters;
    }

    @Data
    public static class DigestResponse {
        private String status;
        private String message;
        private String requestId;

        public DigestResponse(String status, String message, String requestId) {
            this.status = status;
            this.message = message;
            this.requestId = requestId;
        }
    }

    @Data
    public static class JobStatus {
        private String status; // processing, completed, failed
        private Instant timestamp;
        private String email;
        private Map<String, String> metadata;

        public JobStatus(String status, Instant timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }

        public JobStatus(String status, Instant timestamp, String email, Map<String, String> metadata) {
            this.status = status;
            this.timestamp = timestamp;
            this.email = email;
            this.metadata = metadata;
        }
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }
}