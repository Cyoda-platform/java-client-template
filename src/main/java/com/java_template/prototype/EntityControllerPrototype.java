package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/digest")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    @PostMapping("/request")
    public ResponseEntity<DigestRequestResponse> receiveDigestRequest(
            @RequestBody @Valid DigestRequestDTO request) {
        logger.info("Received digest request for email: {}", request.getEmail());
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request))
                .exceptionally(ex -> {
                    logger.error("Error processing job {}: {}", jobId, ex.getMessage());
                    entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
                    return null;
                });
        return ResponseEntity.accepted()
                .body(new DigestRequestResponse("accepted",
                        "Digest request received and processing started.",
                        jobId));
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<JobStatusResponse> getDigestStatus(
            @PathVariable("requestId") @NotBlank String requestId) {
        logger.info("Status request for jobId: {}", requestId);
        JobStatus status = entityJobs.get(requestId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request ID not found");
        }
        String sentAt = status.getFinishedAt() != null ? status.getFinishedAt().toString() : null;
        return ResponseEntity.ok(new JobStatusResponse(
                requestId,
                status.getStatus(),
                status.getEmail(),
                sentAt));
    }

    private void processDigestRequest(String jobId, DigestRequestDTO request) {
        logger.info("Processing jobId: {}", jobId);
        try {
            JsonNode data = fetchPetstoreData(request);
            String content = compileDigest(data);
            sendEmail(request.getEmail(), content);
            entityJobs.put(jobId, new JobStatus("completed", Instant.now(), request.getEmail()));
            logger.info("Job {} completed", jobId);
        } catch (Exception e) {
            logger.error("Failed job {}: {}", jobId, e.getMessage(), e);
            entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
        }
    }

    private JsonNode fetchPetstoreData(DigestRequestDTO request) throws Exception {
        String baseUrl = "https://petstore.swagger.io/v2";
        String endpoint = "/pet/findByStatus?status=available";
        if (request.getPreferredCategories().contains("orders")) {
            // TODO: replace with actual orders endpoint; using pets endpoint for prototype
            logger.info("Orders category requested; defaulting to pets endpoint");
        }
        URI uri = URI.create(baseUrl + endpoint);
        logger.info("Fetching data from: {}", uri);
        String response = restTemplate.getForObject(uri, String.class);
        if (!StringUtils.hasText(response)) {
            throw new IllegalStateException("Empty external API response");
        }
        return objectMapper.readTree(response);
    }

    private String compileDigest(JsonNode data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            logger.error("Error compiling digest: {}", e.getMessage());
            return "Error compiling digest";
        }
    }

    private void sendEmail(String email, String content) {
        logger.info("Sending email to: {}", email);
        logger.info("Content preview: {}", content.substring(0, Math.min(content.length(), 200)) + "...");
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        logger.info("Email sent (mock) to {}", email);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "Internal server error"));
    }

    @Data
    public static class DigestRequestDTO {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String userId;

        @NotBlank
        private String requestTimestamp;

        @NotNull @Size(min = 1)
        private List<@NotBlank String> preferredCategories;
    }

    @Data
    public static class DigestRequestResponse {
        private final String status;
        private final String message;
        private final String requestId;
    }

    @Data
    public static class JobStatusResponse {
        private final String requestId;
        private final String status;
        private final String sentTo;
        private final String sentAt;
    }

    @Data
    public static class JobStatus {
        private final String status;
        private final Instant finishedAt;
        private final String email;
        public JobStatus(String status, Instant finishedAt) {
            this(status, finishedAt, null);
        }
        public JobStatus(String status, Instant finishedAt, String email) {
            this.status = status;
            this.finishedAt = finishedAt;
            this.email = email;
        }
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}