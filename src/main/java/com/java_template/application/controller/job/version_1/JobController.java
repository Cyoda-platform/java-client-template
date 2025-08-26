package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/job/v1/jobs")
@Tag(name = "Job Controller", description = "Dull proxy controller for Job entity (version 1)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Creates a Job entity (triggers event and workflow). Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(
            @RequestBody(description = "Create Job request", required = true, content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateJobRequest request) {
        try {
            // Basic request validation
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getJobName() == null || request.getJobName().isBlank()) {
                throw new IllegalArgumentException("jobName is required");
            }
            if (request.getSource() == null || request.getSource().isBlank()) {
                throw new IllegalArgumentException("source is required");
            }
            if (request.getLocations() == null || request.getLocations().isEmpty()) {
                throw new IllegalArgumentException("locations must be provided");
            }

            // Prepare entity (minimal defaults only; workflows contain business logic)
            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setSource(request.getSource());
            job.setLocations(request.getLocations());
            job.setSchedule(request.getSchedule());
            job.setParameters(request.getParameters());
            // Minimal defaults to satisfy entity validity expectations
            job.setCreatedAt(Instant.now().toString());
            job.setStatus("PENDING");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID id = idFuture.get();
            CreateJobResponse resp = new CreateJobResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in createJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Job", description = "Retrieve job status and summary by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();

            JobSummaryResponse resp = new JobSummaryResponse();
            // Attempt to populate fields from returned object node; fallback to provided id
            if (node != null) {
                if (node.has("id")) resp.setTechnicalId(node.get("id").asText());
                else resp.setTechnicalId(technicalId);

                if (node.has("jobName")) resp.setJobName(node.get("jobName").asText(null));
                if (node.has("status")) resp.setStatus(node.get("status").asText(null));
                if (node.has("createdAt")) resp.setCreatedAt(node.get("createdAt").asText(null));
                if (node.has("processedCount")) resp.setProcessedCount(node.get("processedCount").asInt(0));
                if (node.has("failedCount")) resp.setFailedCount(node.get("failedCount").asInt(0));
            } else {
                resp.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    @Schema(name = "CreateJobRequest", description = "Request payload to create a Job")
    public static class CreateJobRequest {
        @Schema(description = "Human name for the ingestion job", example = "DailyGeoMetIngest", required = true)
        private String jobName;

        @Schema(description = "Data source", example = "MSC GeoMet", required = true)
        private String source;

        @Schema(description = "Location IDs to pull", example = "[\"LOC123\",\"LOC456\"]", required = true)
        private List<String> locations;

        @Schema(description = "Cron or cadence description", example = "every 15 minutes")
        private String schedule;

        @Schema(description = "Extra parameters", example = "{}")
        private Map<String, Object> parameters;
    }

    @Data
    @Schema(name = "CreateJobResponse", description = "Response after creating a Job")
    public static class CreateJobResponse {
        @Schema(description = "Technical ID of the created job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobSummaryResponse", description = "Summary view of a Job")
    public static class JobSummaryResponse {
        @Schema(description = "Technical ID of the job", example = "job-tech-0001")
        private String technicalId;

        @Schema(description = "Human name for the ingestion job", example = "DailyGeoMetIngest")
        private String jobName;

        @Schema(description = "Current job status", example = "COMPLETED")
        private String status;

        @Schema(description = "ISO-8601 creation timestamp", example = "2025-08-26T10:00:00Z")
        private String createdAt;

        @Schema(description = "Number of processed observations", example = "124")
        private Integer processedCount = 0;

        @Schema(description = "Number of failed observations", example = "2")
        private Integer failedCount = 0;
    }
}