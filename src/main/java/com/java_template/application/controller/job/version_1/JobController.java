package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/job/v1/jobs")
@Tag(name = "Job", description = "Job entity API (version 1) - proxy controller")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public JobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Job", description = "Create a Job entity which will trigger its workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = JobCreateRequest.class)))
            @RequestBody JobCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic format validation (no business logic)
            if (request.getType() == null || request.getType().isBlank()) {
                throw new IllegalArgumentException("type is required");
            }
            if (request.getPayload() == null) {
                throw new IllegalArgumentException("payload is required");
            }

            Job job = new Job();
            job.setType(request.getType());
            job.setPayload(request.getPayload());
            job.setScheduledAt(request.getScheduledAt());
            // controller must be a proxy only; do not set workflow/business fields here

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create job: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found during create job: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create job: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating job", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while creating job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Job", description = "Retrieve a Job entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    tid
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            JobResponse resp;
            try {
                resp = objectMapper.treeToValue(node, JobResponse.class);
            } catch (JsonProcessingException e) {
                // Fallback: return raw JSON if mapping fails
                logger.warn("Failed to map Job entity to JobResponse DTO, returning raw JSON", e);
                return ResponseEntity.ok(node);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to get job: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Job not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument while retrieving job: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving job", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    @Schema(name = "JobCreateRequest", description = "Request payload to create a Job")
    public static class JobCreateRequest {
        @Schema(description = "Job type (e.g., ingest, notify)", example = "ingest", required = true)
        private String type;

        @Schema(description = "Job payload", required = true)
        private java.util.Map<String, Object> payload;

        @Schema(description = "Scheduled time (ISO8601)", example = "2025-08-25T12:00:00Z")
        private String scheduledAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity", example = "job_abc123")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job entity response")
    public static class JobResponse {
        @Schema(description = "Technical id", example = "job_abc123")
        private String id;

        @Schema(description = "Job type", example = "ingest")
        private String type;

        @Schema(description = "Job status", example = "completed")
        private String status;

        @Schema(description = "Job payload")
        private java.util.Map<String, Object> payload;

        @Schema(description = "Related pet ids")
        private java.util.List<String> petIds;

        @Schema(description = "Number of attempts", example = "1")
        private Integer attempts;

        @Schema(description = "Job result object")
        private java.util.Map<String, Object> result;

        @Schema(description = "Created at timestamp (ISO8601)", example = "2025-08-25T12:00:00Z")
        private String createdAt;

        @Schema(description = "Updated at timestamp (ISO8601)", example = "2025-08-25T12:01:00Z")
        private String updatedAt;

        @Schema(description = "Last error message if any")
        private String lastError;

        @Schema(description = "Scheduled at timestamp (ISO8601)", example = "2025-08-25T12:00:00Z")
        private String scheduledAt;

        @Schema(description = "Subscriber ids related to the job")
        private java.util.List<String> subscriberIds;
    }
}