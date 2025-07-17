package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.Job;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@Validated
@RequestMapping(path = "/prototype/job")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/request")
    public CompletableFuture<ResponseEntity<DigestResponse>> createDigestRequest(@RequestBody @Valid DigestRequest request) {
        logger.info("Received digest request for email: {}", request.getEmail());
        Job job = new Job();
        job.setStatus("processing");
        job.setTimestamp(Instant.now());
        job.setEmail(request.getEmail());
        job.setMetadataJson(request.getMetadataJson());

        // Persist the Job entity, process logic moved to processors
        return entityService.addItem("Job", ENTITY_VERSION, job)
                .thenApply(technicalId -> {
                    String jobId = technicalId.toString();
                    // Business logic moved to processors - no local processing here
                    return ResponseEntity.ok(new DigestResponse("success", "Digest request accepted.", jobId));
                });
    }

    @GetMapping("/status/{requestId}")
    public CompletableFuture<ResponseEntity<Job>> getDigestStatus(@PathVariable("requestId") @NotBlank String requestId) {
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
                        Job jobStatus = objectMapper.treeToValue(objectNode, Job.class);
                        return ResponseEntity.ok(jobStatus);
                    } catch (Exception e) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse Job status");
                    }
                });
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
        @Email
        @NotBlank
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
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}