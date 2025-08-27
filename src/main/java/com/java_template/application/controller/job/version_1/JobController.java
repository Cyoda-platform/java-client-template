package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "JobController", description = "Controller for Job entity (version 1)")
@Validated
public class JobController {
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a new Job which will be scheduled for ingestion. Returns the technicalId (UUID) of the persisted Job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
            @Valid @RequestBody CreateJobRequest request) {
        try {
            // Basic format validation
            if (request.getJobId() == null || request.getJobId().isBlank()) {
                throw new IllegalArgumentException("jobId must be provided");
            }
            if (request.getSourceEndpoint() == null || request.getSourceEndpoint().isBlank()) {
                throw new IllegalArgumentException("sourceEndpoint must be provided");
            }

            Job job = new Job();
            job.setJobId(request.getJobId());
            job.setSourceEndpoint(request.getSourceEndpoint());
            job.setScheduledAt(request.getScheduledAt());
            // Initial state should be set so entity.isValid() passes and processors can start
            job.setState("SCHEDULED");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            java.util.UUID technicalId = idFuture.get();
            CreateJobResponse resp = new CreateJobResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid create job request: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating job", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating job", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve Job by its technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<JobResponse> getJobByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            UUID techUUID = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    techUUID
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.notFound().build();
            }

            // Convert to Job entity then map to response using getters
            Job job = objectMapper.treeToValue(node, Job.class);

            JobResponse resp = new JobResponse();
            resp.setTechnicalId(technicalId);
            resp.setJobId(job.getJobId());
            resp.setScheduledAt(job.getScheduledAt());
            resp.setStartedAt(job.getStartedAt());
            resp.setFinishedAt(job.getFinishedAt());
            resp.setState(job.getState());
            resp.setRecordsFetchedCount(job.getRecordsFetchedCount());
            resp.setErrorDetails(job.getErrorDetails());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid get job request: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving job", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving job", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving job", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    @Schema(name = "CreateJobRequest", description = "Payload to create a Job")
    public static class CreateJobRequest {
        @Schema(description = "Business job identifier (external)", example = "job-20250827-001", required = true)
        private String jobId;

        @Schema(description = "When ingestion is scheduled (ISO datetime)", example = "2025-08-27T09:00:00Z")
        private String scheduledAt;

        @Schema(description = "Source API endpoint URL", example = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=100", required = true)
        private String sourceEndpoint;
    }

    @Data
    @Schema(name = "CreateJobResponse", description = "Response after creating a Job")
    public static class CreateJobResponse {
        @Schema(description = "Technical identifier (UUID) of the persisted job", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job details returned from storage")
    public static class JobResponse {
        @Schema(description = "Technical identifier (UUID) of the persisted job", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;

        @Schema(description = "Business job identifier (external)", example = "job-20250827-001")
        private String jobId;

        @Schema(description = "When ingestion was scheduled (ISO datetime)")
        private String scheduledAt;

        @Schema(description = "When ingestion started (ISO datetime)")
        private String startedAt;

        @Schema(description = "When ingestion finished (ISO datetime)")
        private String finishedAt;

        @Schema(description = "Current lifecycle state", example = "NOTIFIED_SUBSCRIBERS")
        private String state;

        @Schema(description = "Number of laureate records processed", example = "12")
        private Integer recordsFetchedCount;

        @Schema(description = "Error details if any")
        private String errorDetails;
    }
}