package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.job.version_1.Job;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/job/v1/jobs")
@Tag(name = "Job Controller", description = "Controller proxying Job entity operations")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a Job entity. Returns the technicalId of the created entity.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = JobCreateRequest.class)))
            @RequestBody JobCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getJobId() == null || request.getJobId().isBlank()) {
                throw new IllegalArgumentException("jobId is required");
            }
            if (request.getScheduledAt() == null || request.getScheduledAt().isBlank()) {
                throw new IllegalArgumentException("scheduledAt is required");
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }

            // Build entity - controller should not implement business rules.
            Job entity = new Job();
            entity.setJobId(request.getJobId());
            entity.setScheduledAt(request.getScheduledAt());
            entity.setSourceUrl(request.getSourceUrl());
            // Set a default status so entity.isValid() passes; workflow will handle transitions.
            entity.setStatus("SCHEDULED");
            // startedAt/finishedAt/summary left null

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    Job.ENTITY_VERSION,
                    entity
            );
            UUID createdId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(createdId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create Job: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create: {}", cause.getMessage());
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during createJob", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Job", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating Job", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job entity by its technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JobResponse> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(id);
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.notFound().build();
            }
            JobResponse response = objectMapper.treeToValue(node, JobResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get Job: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Job not found: {}", cause.getMessage());
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument when retrieving Job: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during getJob", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving Job", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving Job", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    @Schema(name = "JobCreateRequest", description = "Request payload to create a Job")
    public static class JobCreateRequest {
        @Schema(description = "Business job identifier", example = "daily-nobel-ingest", required = true)
        private String jobId;
        @Schema(description = "Source URL to ingest", example = "https://example.com/api/data", required = true)
        private String sourceUrl;
        @Schema(description = "ISO datetime when job is scheduled", example = "2025-09-01T10:00:00Z", required = true)
        private String scheduledAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical identifier of the created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Read representation of a Job entity")
    public static class JobResponse {
        @Schema(description = "Business job identifier", example = "daily-nobel-ingest")
        private String jobId;
        @Schema(description = "Job status", example = "NOTIFIED_SUBSCRIBERS")
        private String status;
        @Schema(description = "ISO datetime when job was scheduled", example = "2025-09-01T10:00:00Z")
        private String scheduledAt;
        @Schema(description = "ISO datetime when job started", example = "2025-09-01T10:00:05Z")
        private String startedAt;
        @Schema(description = "ISO datetime when job finished", example = "2025-09-01T10:00:20Z")
        private String finishedAt;
        @Schema(description = "Brief summary of the job run", example = "ingested 5 laureates")
        private String summary;
        @Schema(description = "Source URL used by the job", example = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records")
        private String sourceUrl;
    }
}