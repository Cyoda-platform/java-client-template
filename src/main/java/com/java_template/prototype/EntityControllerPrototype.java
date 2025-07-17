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
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping(path = "/prototype/digest")
@RequiredArgsConstructor
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/request")
    public ResponseEntity<DigestRequestResponse> submitDigestRequest(@RequestBody @Valid DigestRequest request) {
        logger.info("Received digest request for email={} digestType={} status={}", request.getEmail(), request.getDigestType(), request.getStatus());
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request));
        return ResponseEntity.accepted().body(new DigestRequestResponse(jobId, "accepted"));
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<JobStatusResponse> getDigestStatus(@PathVariable("requestId") @NotBlank String requestId) {
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

    @Async
    protected void processDigestRequest(String jobId, DigestRequest request) {
        logger.info("Processing digest jobId={} for email={}", jobId, request.getEmail());
        try {
            String digestType = request.getDigestType();
            String statusParam = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "available";
            String apiUrl;
            if ("petStatusDigest".equalsIgnoreCase(digestType)) {
                apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam;
            } else {
                apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam;
            }
            logger.info("Fetching external data from {}", apiUrl);
            String rawJson = restTemplate.getForObject(URI.create(apiUrl), String.class);
            if (rawJson == null) {
                throw new IllegalStateException("Empty response from external API");
            }
            JsonNode dataNode = objectMapper.readTree(rawJson);
            String digestHtml = compileDigestHtml(dataNode);
            sendEmail(request.getEmail(), "Your Pet Status Digest", digestHtml);
            entityJobs.put(jobId, new JobStatus(
                    "completed",
                    Instant.now(),
                    request.getEmail(),
                    "Sent pet status digest with " + (dataNode.isArray() ? dataNode.size() : 1) + " entries"
            ));
            logger.info("Digest jobId={} completed successfully", jobId);
        } catch (Exception e) {
            logger.error("Error processing digest jobId={}: {}", jobId, e.toString());
            entityJobs.put(jobId, new JobStatus(
                    "failed",
                    Instant.now(),
                    request.getEmail(),
                    "Error: " + e.getMessage()
            ));
        }
    }

    private String compileDigestHtml(JsonNode dataNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><h1>Pet Status Digest</h1><ul>");
        if (dataNode.isArray()) {
            for (JsonNode pet : dataNode) {
                String name = pet.has("name") ? pet.get("name").asText() : "Unnamed";
                String status = pet.has("status") ? pet.get("status").asText() : "unknown";
                sb.append("<li>").append("Pet: ").append(name).append(", Status: ").append(status).append("</li>");
            }
        } else {
            sb.append("<li>Pet data unavailable</li>");
        }
        sb.append("</ul></body></html>");
        return sb.toString();
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        logger.info("Mock sending email to={} subject={}", toEmail, subject);
        // TODO: Replace with real email service integration
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.toString());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        logger.error("Unexpected exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error"));
    }

    @Data
    public static class DigestRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String digestType;
        @NotBlank
        private String status;
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
        public JobStatus(String status, Instant sentAt) {
            this.status = status;
            this.sentAt = sentAt;
            this.email = null;
            this.digestSummary = null;
        }
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