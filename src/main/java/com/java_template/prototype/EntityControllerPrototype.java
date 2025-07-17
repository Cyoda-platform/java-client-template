package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping(path = "/prototype/digest")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String DEFAULT_ENDPOINT = "/pet/findByStatus";
    private static final Map<String, String> DEFAULT_PARAMETERS = Map.of("status", "available");

    @PostMapping("/request")
    public ResponseEntity<DigestResponse> createDigestRequest(@RequestBody @Valid DigestRequest request) {
        logger.info("Received digest request for email: {}", request.getEmail());
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt, request.getEmail(), request.getMetadataJson()));
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request)); // fire-and-forget
        return ResponseEntity.ok(new DigestResponse("success", "Digest request accepted.", jobId));
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<JobStatus> getDigestStatus(@PathVariable("requestId") @NotBlank String requestId) {
        JobStatus status = entityJobs.get(requestId);
        if (status == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Request ID not found");
        }
        return ResponseEntity.ok(status);
    }

    private void processDigestRequest(String jobId, DigestRequest request) {
        try {
            logger.info("Start processing job {}", jobId);
            String endpoint = StringUtils.hasText(request.getEndpoint()) ? request.getEndpoint() : DEFAULT_ENDPOINT;
            Map<String, String> parameters = DEFAULT_PARAMETERS;
            if (StringUtils.hasText(request.getParametersJson())) {
                JsonNode paramsNode = objectMapper.readTree(request.getParametersJson());
                parameters = objectMapper.convertValue(paramsNode, Map.class);
            }
            StringBuilder url = new StringBuilder("https://petstore.swagger.io/v2").append(endpoint);
            if (!parameters.isEmpty()) {
                url.append("?");
                parameters.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
                url.deleteCharAt(url.length() - 1);
            }
            String apiResponse = restTemplate.getForObject(url.toString(), String.class);
            JsonNode dataNode = objectMapper.readTree(apiResponse);
            String content = compileEmailContent(dataNode, request.getEmailFormat());
            sendEmail(request.getEmail(), content, request.getEmailFormat());
            entityJobs.put(jobId, new JobStatus("completed", Instant.now(), request.getEmail(), request.getMetadataJson()));
            logger.info("Completed job {}", jobId);
        } catch (Exception e) {
            logger.error("Error in job {}: {}", jobId, e.getMessage(), e);
            entityJobs.put(jobId, new JobStatus("failed", Instant.now(), request.getEmail(), request.getMetadataJson()));
        }
    }

    private String compileEmailContent(JsonNode dataNode, String format) {
        if ("plain".equalsIgnoreCase(format)) {
            return dataNode.toPrettyString();
        } else if ("attachment".equalsIgnoreCase(format)) {
            return "Attachment not implemented.\n" + dataNode.toPrettyString();
        } else {
            return "<html><body><pre>" + dataNode.toPrettyString() + "</pre></body></html>";
        }
    }

    private void sendEmail(String to, String content, String format) {
        logger.info("Mock send email to {} format {}", to, format);
        logger.info("Content preview: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal error"));
    }

    public static class DigestRequest {
        @Email @NotBlank
        private String email;
        @NotBlank
        private String metadataJson;
        private String endpoint;
        private String parametersJson;
        @Pattern(regexp = "plain|html|attachment")
        private String emailFormat;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getMetadataJson() { return metadataJson; }
        public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getParametersJson() { return parametersJson; }
        public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
        public String getEmailFormat() { return emailFormat; }
        public void setEmailFormat(String emailFormat) { this.emailFormat = emailFormat; }
    }

    public static class DigestResponse {
        private String status;
        private String message;
        private String requestId;
        public DigestResponse(String status, String message, String requestId) {
            this.status = status;
            this.message = message;
            this.requestId = requestId;
        }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getRequestId() { return requestId; }
    }

    public static class JobStatus {
        private String status;
        private Instant timestamp;
        private String email;
        private String metadataJson;
        public JobStatus(String status, Instant timestamp, String email, String metadataJson) {
            this.status = status;
            this.timestamp = timestamp;
            this.email = email;
            this.metadataJson = metadataJson;
        }
        public String getStatus() { return status; }
        public Instant getTimestamp() { return timestamp; }
        public String getEmail() { return email; }
        public String getMetadataJson() { return metadataJson; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
        public String getError() { return error; }
        public String getMessage() { return message; }
    }
}