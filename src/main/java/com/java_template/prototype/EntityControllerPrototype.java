package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/prototype/digest")
@Validated
@RequiredArgsConstructor
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String DEFAULT_ENDPOINT = "/pet/findByStatus";
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    @PostMapping(value = "/request", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestRequestResponse submitDigestRequest(@RequestBody @Valid DigestRequest request) {
        logger.info("Received digest request for email: {}", request.getEmail());
        Instant requestedAt = Instant.now();
        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt.toString(), null, null, null, null));
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request, requestedAt));
        return new DigestRequestResponse("accepted", "Digest request received and processing started", jobId);
    }

    @GetMapping(value = "/status/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JobStatus getDigestStatus(@PathVariable("requestId") String requestId) {
        JobStatus status = entityJobs.get(requestId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request ID not found");
        }
        return status;
    }

    @Async
    void processDigestRequest(String jobId, DigestRequest request, Instant requestedAt) {
        logger.info("Started processing digest for jobId={}", jobId);
        try {
            String endpoint = StringUtils.hasText(request.getEndpoint()) ? request.getEndpoint() : DEFAULT_ENDPOINT;
            String statusParam = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "available";
            StringBuilder uriBuilder = new StringBuilder(PETSTORE_BASE_URL).append(endpoint)
                .append("?status=").append(statusParam);
            URI uri = URI.create(uriBuilder.toString());
            logger.info("Calling external API: {}", uri);
            String rawJson = restTemplate.getForObject(uri, String.class);
            if (rawJson == null) throw new IllegalStateException("External API returned empty response");
            JsonNode dataNode = objectMapper.readTree(rawJson);
            String digestHtml = compileDigestHtml(dataNode);
            sendEmail(request.getEmail(), digestHtml);
            entityJobs.put(jobId, new JobStatus("completed", requestedAt.toString(), Instant.now().toString(), request.getEmail(), digestHtml, null));
            logger.info("Completed processing digest for jobId={}", jobId);
        } catch (Exception e) {
            logger.error("Error processing digest for jobId=" + jobId, e);
            entityJobs.put(jobId, new JobStatus("failed", requestedAt.toString(), Instant.now().toString(), null, null, e.getMessage()));
        }
    }

    private String compileDigestHtml(JsonNode dataNode) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><h2>Petstore Digest</h2><pre>")
            .append(dataNode.toPrettyString())
            .append("</pre></body></html>");
        return html.toString();
    }

    private void sendEmail(String toEmail, String htmlContent) {
        logger.info("Sending email to: {} content preview: {}", toEmail, htmlContent.substring(0, Math.min(200, htmlContent.length())) + "...");
        // TODO: replace with real email sending
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        String statusCode = ex.getStatusCode().toString();
        return new ErrorResponse(statusCode, ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneralException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
    }

    @Data
    public static class DigestRequest {
        @NotBlank @Email
        private String email;
        @Pattern(regexp = "^/.*", message = "must start with /") 
        private String endpoint;
        @Size(min = 1, message = "status must not be empty")
        private String status;
    }

    @Data
    public static class DigestRequestResponse {
        private final String status;
        private final String message;
        private final String requestId;
    }

    @Data
    public static class JobStatus {
        private final String status;
        private final String requestedAt; // ISO timestamp
        private final String completedAt; // ISO timestamp
        private final String email;
        private final String digestSummary;
        private final String error;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}