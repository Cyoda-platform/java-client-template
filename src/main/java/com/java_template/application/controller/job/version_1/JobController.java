package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for Job entity. All business logic handled in workflows.
 */
@RestController
@RequestMapping(path = "/api/v1/jobs")
@Tag(name = "Job", description = "Job entity proxy API (version 1)")
@RequiredArgsConstructor
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Job", description = "Persist a Job entity and trigger Job workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
            @RequestBody CreateJobRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Job job = new Job();
            // Map allowed fields from request; controller must not implement business logic.
            job.setId(request.getId());
            job.setScheduleDefinition(request.getScheduleDefinition());
            job.setTriggeredBy(request.getTriggeredBy());

            if (request.getNotificationPolicy() != null) {
                Job.NotificationPolicy np = new Job.NotificationPolicy();
                np.setType(request.getNotificationPolicy().getType());
                job.setNotificationPolicy(np);
            }

            if (request.getRetryPolicy() != null) {
                Job.RetryPolicy rp = new Job.RetryPolicy();
                rp.setBackoffSeconds(request.getRetryPolicy().getBackoffSeconds());
                rp.setMaxAttempts(request.getRetryPolicy().getMaxAttempts());
                job.setRetryPolicy(rp);
            }

            // Delegate persistence to EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            CreateJobResponse resp = new CreateJobResponse();
            resp.setTechnicalId(technicalId.toString());
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
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job by its technical datastore id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}")
    public ResponseEntity<JobResponse> getJobByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();

            if (node == null || node.isNull()) {
                return ResponseEntity.notFound().build();
            }

            JobResponse resp = objectMapper.treeToValue(node, JobResponse.class);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getJobByTechnicalId: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in getJobByTechnicalId", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getJobByTechnicalId", e);
            return ResponseEntity.status(500).build();
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateJobRequest", description = "Request payload to create a Job")
    public static class CreateJobRequest {
        @Schema(description = "Business id for the job", example = "job-2025-08-01")
        private String id;

        @Schema(description = "Schedule definition (cron/interval/manual)", example = "manual")
        private String scheduleDefinition;

        @Schema(description = "Notification policy")
        private NotificationPolicyDto notificationPolicy;

        @Schema(description = "Retry policy")
        private RetryPolicyDto retryPolicy;

        @Schema(description = "Triggered by (system or user)", example = "user")
        private String triggeredBy;
    }

    @Data
    @Schema(name = "CreateJobResponse", description = "Response containing created Job technical id")
    public static class CreateJobResponse {
        @Schema(description = "Technical id of created Job", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job entity representation returned by GET")
    public static class JobResponse {
        @Schema(description = "Technical id", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;

        @Schema(description = "Business id", example = "job-2025-08-01")
        private String id;

        @Schema(description = "Current lifecycle status", example = "NOTIFIED_SUBSCRIBERS")
        private String status;

        @Schema(description = "Schedule definition")
        private String scheduleDefinition;

        @Schema(description = "Triggered by")
        private String triggeredBy;

        @Schema(description = "ISO timestamp when started", example = "2025-08-25T12:00:00Z")
        private String startedAt;

        @Schema(description = "ISO timestamp when finished", example = "2025-08-25T12:05:00Z")
        private String finishedAt;

        @Schema(description = "Error details if failed")
        private String errorDetails;

        @Schema(description = "Ingestion summary")
        private IngestionSummaryDto ingestionSummary;

        @Schema(description = "Notification policy")
        private NotificationPolicyDto notificationPolicy;

        @Schema(description = "Retry policy")
        private RetryPolicyDto retryPolicy;
    }

    @Data
    @Schema(name = "IngestionSummary", description = "Summary of ingestion results")
    public static class IngestionSummaryDto {
        @Schema(description = "Records failed", example = "1")
        private Integer recordsFailed;

        @Schema(description = "Records fetched", example = "10")
        private Integer recordsFetched;

        @Schema(description = "Records processed", example = "9")
        private Integer recordsProcessed;
    }

    @Data
    @Schema(name = "NotificationPolicy", description = "Notification policy")
    public static class NotificationPolicyDto {
        @Schema(description = "Policy type", example = "allSubscribers")
        private String type;
    }

    @Data
    @Schema(name = "RetryPolicy", description = "Retry policy")
    public static class RetryPolicyDto {
        @Schema(description = "Backoff seconds", example = "60")
        private Integer backoffSeconds;

        @Schema(description = "Maximum attempts", example = "3")
        private Integer maxAttempts;
    }
}