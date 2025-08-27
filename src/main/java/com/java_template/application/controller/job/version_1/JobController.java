package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
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
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Job Controller", description = "CRUD endpoints for Job entity (version 1) - proxy to EntityService")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public JobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Job", description = "Create a new Job. This will persist the Job entity and return the technicalId (UUID). Business/workflow logic is handled asynchronously by the workflows.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<JobCreateResponse> createJob(
            @RequestBody(description = "Job creation payload", required = true, content = @Content(schema = @Schema(implementation = JobCreateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody JobCreateRequest request
    ) {
        try {
            // Basic request validation
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }
            if (request.getSchedule() == null || request.getSchedule().isBlank()) {
                throw new IllegalArgumentException("schedule is required");
            }
            if (request.getNotifyOn() == null || request.getNotifyOn().isBlank()) {
                throw new IllegalArgumentException("notifyOn is required");
            }

            // Create entity and set minimal required fields. No business logic here.
            Job job = new Job();
            // business id required by entity validation - generate a simple UUID string business id
            job.setJobId(UUID.randomUUID().toString());
            job.setSourceUrl(request.getSourceUrl());
            job.setSchedule(request.getSchedule());
            job.setNotifyOn(request.getNotifyOn());
            job.setStatus("SCHEDULED");
            job.setCreatedAt(Instant.now().toString());
            if (request.getRetryPolicy() != null) {
                Job.RetryPolicy rp = new Job.RetryPolicy();
                rp.setBackoffSeconds(request.getRetryPolicy().getBackoffSeconds());
                rp.setMaxRetries(request.getRetryPolicy().getMaxRetries());
                job.setRetryPolicy(rp);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, Job.ENTITY_VERSION, job);
            UUID persistedId = idFuture.get();

            JobCreateResponse resp = new JobCreateResponse();
            resp.setTechnicalId(persistedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in createJob", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in createJob", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job entity by its technicalId (UUID). Returns Job details.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<JobResponse> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(uuid);
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).build();
            }
            JsonNode dataNode = (JsonNode) dataPayload.getData();
            JobResponse response = objectMapper.treeToValue(dataNode, JobResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getJobById: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in getJobById", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getJobById", ex);
            return ResponseEntity.status(500).build();
        }
    }

    // Static DTO classes for request/response payloads

    @Data
    @Schema(name = "JobCreateRequest", description = "Payload to create a Job")
    public static class JobCreateRequest {
        @Schema(description = "Schedule, e.g., ON_DEMAND or cron expression", example = "ON_DEMAND")
        private String schedule;

        @Schema(description = "Source URL for ingest", example = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records")
        private String sourceUrl;

        @Schema(description = "Notify on: SUCCESS, FAILURE, BOTH", example = "BOTH")
        private String notifyOn;

        @Schema(description = "Retry policy")
        private RetryPolicyDto retryPolicy;
    }

    @Data
    @Schema(name = "RetryPolicyDto", description = "Retry policy for job ingestion")
    public static class RetryPolicyDto {
        @Schema(description = "Maximum retries", example = "3")
        private Integer maxRetries;
        @Schema(description = "Backoff seconds", example = "60")
        private Integer backoffSeconds;
    }

    @Data
    @Schema(name = "JobCreateResponse", description = "Response after creating a Job")
    public static class JobCreateResponse {
        @Schema(description = "Technical ID (UUID) of the persisted Job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job representation returned by the API")
    public static class JobResponse {
        @Schema(description = "Business job id", example = "job-0001")
        private String jobId;

        @Schema(description = "Job status", example = "NOTIFIED_SUBSCRIBERS")
        private String status;

        @Schema(description = "Schedule", example = "ON_DEMAND")
        private String schedule;

        @Schema(description = "Source URL", example = "https://.../records")
        private String sourceUrl;

        @Schema(description = "Started at timestamp (ISO-8601)", example = "2025-08-01T12:00:00Z")
        private String startedAt;

        @Schema(description = "Finished at timestamp (ISO-8601)", example = "2025-08-01T12:00:10Z")
        private String finishedAt;

        @Schema(description = "Ingest result summary")
        private IngestResultDto ingestResult;
    }

    @Data
    @Schema(name = "IngestResultDto", description = "Result information about an ingest")
    public static class IngestResultDto {
        @Schema(description = "Number of items added", example = "5")
        private Integer countAdded;

        @Schema(description = "Number of items updated", example = "2")
        private Integer countUpdated;

        @Schema(description = "List of errors (if any)")
        private java.util.List<String> errors;
    }
}