package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.job.version_1.Job;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import java.time.Instant;

/**
 * Dull proxy controller for Job entity. All business logic is implemented in workflows.
 */
@RestController
@RequestMapping("/jobs")
@Tag(name = "Job Controller", description = "Proxy endpoints for Job entity (version 1)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Persist a new Job entity. Returns only the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PostJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJob(
            @RequestBody(description = "Job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = PostJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody PostJobRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }
            if (request.getSchedule() == null || request.getSchedule().isBlank()) {
                throw new IllegalArgumentException("schedule is required");
            }

            // Build Job entity (no business logic here; minimal required fields set)
            Job job = new Job();
            // ensure there is an id for entity validation
            job.setId(UUID.randomUUID().toString());
            job.setSchedule(request.getSchedule());
            job.setSourceUrl(request.getSourceUrl());
            job.setCreatedBy(request.getCreatedBy());
            job.setRunTimestamp(Instant.now().toString()); // minimal required timestamp
            job.setState("SCHEDULED");

            // Persist via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    Job.ENTITY_VERSION,
                    job
            );
            UUID persistedId = idFuture.get();

            PostJobResponse resp = new PostJobResponse();
            resp.setTechnicalId(persistedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create job: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Create job caused NoSuchElementException: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Create job caused IllegalArgumentException: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating job", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating job", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Job", description = "Retrieve stored Job by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(id);
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("Job not found");
            }

            // Convert payload to response DTO
            JsonNode dataNode = dataPayload.getData();
            JobResponse response = objectMapper.treeToValue(dataNode, JobResponse.class);
            // Ensure technicalId is set to requested id
            response.setTechnicalId(technicalId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get job: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Get job caused NoSuchElementException: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Get job caused IllegalArgumentException: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving job", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving job", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Static DTO classes for requests/responses

    @Data
    @Schema(name = "PostJobRequest", description = "Request payload for creating a Job")
    public static class PostJobRequest {
        @Schema(description = "Cron expression or one-off schedule", example = "0 0 * * *")
        private String schedule;

        @Schema(description = "Source URL to ingest", example = "https://public.opendatasoft.com/api/.../records")
        private String sourceUrl;

        @Schema(description = "User who created the job", example = "admin@example.com")
        private String createdBy;
    }

    @Data
    @Schema(name = "PostJobResponse", description = "Response payload after creating a Job")
    public static class PostJobResponse {
        @Schema(description = "Technical ID of the created job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job retrieval response")
    public static class JobResponse {
        @Schema(description = "Technical ID of the job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Cron expression or one-off schedule", example = "0 0 * * *")
        private String schedule;

        @Schema(description = "Current job state", example = "NOTIFIED_SUBSCRIBERS")
        private String state;

        @Schema(description = "Timestamp when job started (ISO-8601)", example = "2025-08-01T10:00:00Z")
        private String runTimestamp;

        @Schema(description = "Timestamp when job completed (ISO-8601)", example = "2025-08-01T10:00:45Z")
        private String completedTimestamp;

        @Schema(description = "Job summary")
        private JobSummary summary;
    }

    @Data
    @Schema(name = "JobSummary", description = "Summary of ingestion results")
    public static class JobSummary {
        @Schema(description = "Number of successfully ingested records", example = "10")
        private Integer ingestedCount;

        @Schema(description = "Number of failed records", example = "0")
        private Integer failedCount;

        @Schema(description = "List of errors", example = "[]")
        private java.util.List<String> errors;
    }
}