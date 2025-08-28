package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.job.version_1.Job;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Job entity. All business logic resides in workflows/processors.
 */
@RestController
@RequestMapping("/jobs")
@Tag(name = "Job Controller", description = "CRUD endpoints for Job entity (proxy to EntityService)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a new Job orchestration entity. Returns technicalId only.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateJobResponse.class)))
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Create Job request",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateJobRequest.class))
            )
            @RequestBody CreateJobRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getJobId() == null || request.getJobId().isBlank()) {
                throw new IllegalArgumentException("jobId is required");
            }
            if (request.getScheduleAt() == null || request.getScheduleAt().isBlank()) {
                throw new IllegalArgumentException("scheduleAt is required");
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }

            // Create entity instance and populate basic fields only (controller must remain a proxy).
            Job jobEntity = new Job();
            jobEntity.setJobId(request.getJobId());
            jobEntity.setScheduleAt(request.getScheduleAt());
            jobEntity.setSourceUrl(request.getSourceUrl());

            // Minimal non-business defaults to keep entity consistent for persistence
            jobEntity.setState("SCHEDULED");
            jobEntity.setTotalRecords(0);
            jobEntity.setSucceededCount(0);
            jobEntity.setFailedCount(0);
            jobEntity.setStartedAt(null);
            jobEntity.setFinishedAt(null);
            jobEntity.setErrorSummary(null);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    Job.ENTITY_VERSION,
                    jobEntity
            );
            UUID createdId = idFuture.get();

            CreateJobResponse resp = new CreateJobResponse();
            resp.setTechnicalId(createdId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createJob request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new CreateJobResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new CreateJobResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when creating Job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job entity by its technicalId")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class)))
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "404", description = "Not Found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    @GetMapping("/{technicalId}")
    public ResponseEntity<JobResponse> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            JsonNode dataNode = dataPayload.getData();
            JobResponse response = objectMapper.treeToValue(dataNode, JobResponse.class);
            // Ensure technicalId is present in response
            response.setTechnicalId(technicalId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getJobById request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when retrieving Job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving Job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "CreateJobRequest", description = "Request to create a Job")
    public static class CreateJobRequest {
        @Schema(description = "User-supplied job id", example = "job-2025-08-28-01", required = true)
        private String jobId;

        @Schema(description = "ISO datetime when job should run", example = "2025-08-28T10:00:00Z", required = true)
        private String scheduleAt;

        @Schema(description = "Source URL to ingest", example = "https://example.com/data.json", required = true)
        private String sourceUrl;
    }

    @Data
    @Schema(name = "CreateJobResponse", description = "Response for Job creation")
    public static class CreateJobResponse {
        @Schema(description = "Technical id of the created entity")
        private String technicalId;
        // Optional message for error signalling in body when returning 400 with payload
        @Schema(description = "Optional message")
        private String message;

        public CreateJobResponse() {}

        public CreateJobResponse(String message) {
            this.message = message;
        }
    }

    @Data
    @Schema(name = "JobResponse", description = "Representation of stored Job entity")
    public static class JobResponse {
        @Schema(description = "Technical id of the entity")
        private String technicalId;

        @Schema(description = "User-supplied job id")
        private String jobId;

        @Schema(description = "ISO datetime when job was scheduled")
        private String scheduleAt;

        @Schema(description = "ISO datetime when ingestion started")
        private String startedAt;

        @Schema(description = "ISO datetime when ingestion finished")
        private String finishedAt;

        @Schema(description = "Lifecycle state")
        private String state;

        @Schema(description = "Source URL ingested from")
        private String sourceUrl;

        @Schema(description = "Total records processed")
        private Integer totalRecords;

        @Schema(description = "Count succeeded")
        private Integer succeededCount;

        @Schema(description = "Count failed")
        private Integer failedCount;

        @Schema(description = "Short description of failures")
        private String errorSummary;
    }
}