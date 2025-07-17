package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping(path = "/prototype/job")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/request")
    public CompletableFuture<ResponseEntity<DigestResponse>> createDigestRequest(@RequestBody @Valid DigestRequest request) {
        logger.info("Received digest request for email: {}", request.getEmail());
        JobStatus jobStatus = new JobStatus("processing", Instant.now(), request.getEmail(), request.getMetadataJson());
        return entityService.addItem("Job", ENTITY_VERSION, jobStatus)
                .thenApply(technicalId -> {
                    String jobId = technicalId.toString();
                    processDigestRequest(jobId, request);
                    return ResponseEntity.ok(new DigestResponse("success", "Digest request accepted.", jobId));
                });
    }

    @GetMapping("/status/{requestId}")
    public CompletableFuture<ResponseEntity<JobStatus>> getDigestStatus(@PathVariable("requestId") @NotBlank String requestId) {
        UUID id;
        try {
            id = UUID.fromString(requestId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Request ID format");
        }
        return entityService.getItem("Job", ENTITY_VERSION, id)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Request ID not found");
                    }
                    try {
                        JobStatus jobStatus = objectMapper.treeToValue(objectNode, JobStatus.class);
                        return ResponseEntity.ok(jobStatus);
                    } catch (Exception e) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse Job status");
                    }
                });
    }

    private void processDigestRequest(String jobId, DigestRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Start processing job {}", jobId);
                String endpoint = StringUtils.hasText(request.getEndpoint()) ? request.getEndpoint() : "/pet/findByStatus";
                Map<String, String> parameters = Map.of("status", "available");
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
                JobStatus completedStatus = new JobStatus("completed", Instant.now(), request.getEmail(), request.getMetadataJson());
                UUID jobUuid = UUID.fromString(jobId);
                entityService.updateItem("Job", ENTITY_VERSION, jobUuid, completedStatus).join();
                logger.info("Completed job {}", jobId);
            } catch (Exception e) {
                logger.error("Error in job {}: {}", jobId, e.getMessage(), e);
                JobStatus failedStatus = new JobStatus("failed", Instant.now(), request.getEmail(), request.getMetadataJson());
                try {
                    UUID jobUuid = UUID.fromString(jobId);
                    entityService.updateItem("Job", ENTITY_VERSION, jobUuid, failedStatus).join();
                } catch (Exception ignored) {
                }
            }
        });
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigestRequest {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String metadataJson;
        private String endpoint;
        private String parametersJson;
        @Pattern(regexp = "plain|html|attachment")
        private String emailFormat;
    }

    @Data
    @AllArgsConstructor
    public static class DigestResponse {
        private String status;
        private String message;
        private String requestId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant timestamp;
        private String email;
        private String metadataJson;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}