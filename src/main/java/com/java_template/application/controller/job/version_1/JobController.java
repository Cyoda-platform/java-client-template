package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
@RequestMapping(path = "/api/v1/jobs", produces = APPLICATION_JSON)
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
    @PostMapping(consumes = APPLICATION_JSON)
    public ResponseEntity<CreateJobResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
            @RequestBody CreateJobRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Job job = mapCreateRequestToJob(request);

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
            return handleExecutionException(ee, "createJob");
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Create multiple Jobs", description = "Persist multiple Job entities and return created technical ids.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateJobsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", consumes = APPLICATION_JSON)
    public ResponseEntity<CreateJobsResponse> createJobsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Job create requests", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateJobRequest.class))))
            @RequestBody List<CreateJobRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body is required and must contain at least one element");
            }

            List<Job> jobs = new ArrayList<>();
            for (CreateJobRequest r : requests) {
                jobs.add(mapCreateRequestToJob(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    jobs
            );

            List<UUID> ids = idsFuture.get();
            CreateJobsResponse resp = new CreateJobsResponse();
            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) technicalIds.add(id.toString());
            }
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJobsBulk: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            return handleExecutionException(ee, "createJobsBulk");
        } catch (Exception e) {
            logger.error("Unexpected error in createJobsBulk", e);
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
            return handleExecutionException(ee, "getJobByTechnicalId");
        } catch (Exception e) {
            logger.error("Unexpected error in getJobByTechnicalId", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get all Jobs", description = "Retrieve all Job entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<JobResponse>> getAllJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            );

            ArrayNode arrayNode = itemsFuture.get();
            List<JobResponse> res = new ArrayList<>();
            if (arrayNode != null) {
                for (int i = 0; i < arrayNode.size(); i++) {
                    ObjectNode node = (ObjectNode) arrayNode.get(i);
                    JobResponse jr = objectMapper.treeToValue(node, JobResponse.class);
                    res.add(jr);
                }
            }
            return ResponseEntity.ok(res);

        } catch (ExecutionException ee) {
            return handleExecutionException(ee, "getAllJobs");
        } catch (Exception e) {
            logger.error("Unexpected error in getAllJobs", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Search Jobs", description = "Retrieve Job entities by search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = APPLICATION_JSON)
    public ResponseEntity<List<JobResponse>> searchJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arrayNode = filteredItemsFuture.get();
            List<JobResponse> res = new ArrayList<>();
            if (arrayNode != null) {
                for (int i = 0; i < arrayNode.size(); i++) {
                    ObjectNode node = (ObjectNode) arrayNode.get(i);
                    JobResponse jr = objectMapper.treeToValue(node, JobResponse.class);
                    res.add(jr);
                }
            }
            return ResponseEntity.ok(res);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchJobs: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            return handleExecutionException(ee, "searchJobs");
        } catch (Exception e) {
            logger.error("Unexpected error in searchJobs", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Job", description = "Update Job entity by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = APPLICATION_JSON)
    public ResponseEntity<UpdateJobResponse> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update request", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateJobRequest.class)))
            @RequestBody UpdateJobRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Job job = mapUpdateRequestToJob(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    job
            );

            UUID updatedId = updatedIdFuture.get();
            UpdateJobResponse resp = new UpdateJobResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            return handleExecutionException(ee, "updateJob");
        } catch (Exception e) {
            logger.error("Unexpected error in updateJob", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Job", description = "Delete Job entity by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<DeleteJobResponse> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID deletedId = deletedIdFuture.get();
            DeleteJobResponse resp = new DeleteJobResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            return handleExecutionException(ee, "deleteJob");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteJob", e);
            return ResponseEntity.status(500).build();
        }
    }

    // --- Helper methods ---

    private Job mapCreateRequestToJob(CreateJobRequest request) {
        Job job = new Job();
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

        return job;
    }

    private Job mapUpdateRequestToJob(UpdateJobRequest request) {
        Job job = new Job();
        job.setId(request.getId());
        job.setScheduleDefinition(request.getScheduleDefinition());
        job.setTriggeredBy(request.getTriggeredBy());
        job.setStatus(request.getStatus());
        job.setStartedAt(request.getStartedAt());
        job.setFinishedAt(request.getFinishedAt());
        job.setErrorDetails(request.getErrorDetails());

        if (request.getIngestionSummary() != null) {
            Job.IngestionSummary is = new Job.IngestionSummary();
            is.setRecordsFailed(request.getIngestionSummary().getRecordsFailed());
            is.setRecordsFetched(request.getIngestionSummary().getRecordsFetched());
            is.setRecordsProcessed(request.getIngestionSummary().getRecordsProcessed());
            job.setIngestionSummary(is);
        }

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

        return job;
    }

    private ResponseEntity handleExecutionException(ExecutionException ee, String context) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            return ResponseEntity.status(404).build();
        } else if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().build();
        } else {
            logger.error("ExecutionException in {}: {}", context, ee.getMessage(), ee);
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
    @Schema(name = "CreateJobsResponse", description = "Response containing created Jobs technical ids")
    public static class CreateJobsResponse {
        @Schema(description = "Technical ids of created Jobs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "UpdateJobRequest", description = "Request payload to update a Job")
    public static class UpdateJobRequest {
        @Schema(description = "Business id for the job", example = "job-2025-08-01")
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
    @Schema(name = "UpdateJobResponse", description = "Response containing updated Job technical id")
    public static class UpdateJobResponse {
        @Schema(description = "Technical id of updated Job", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteJobResponse", description = "Response containing deleted Job technical id")
    public static class DeleteJobResponse {
        @Schema(description = "Technical id of deleted Job", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
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