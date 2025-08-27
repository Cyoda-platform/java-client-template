package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
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

    @Operation(summary = "Create multiple Jobs", description = "Create multiple Jobs in a single request. Returns the technicalIds (UUIDs) of the persisted Jobs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkCreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<BulkCreateJobResponse> createJobsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk job creation payload", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateJobRequest.class))))
            @Valid @RequestBody BulkCreateJobRequest request) {
        try {
            if (request == null || request.getJobs() == null || request.getJobs().isEmpty()) {
                throw new IllegalArgumentException("jobs must be provided");
            }

            List<Job> jobs = new ArrayList<>();
            for (CreateJobRequest r : request.getJobs()) {
                if (r.getJobId() == null || r.getJobId().isBlank()) {
                    throw new IllegalArgumentException("jobId must be provided for each job");
                }
                if (r.getSourceEndpoint() == null || r.getSourceEndpoint().isBlank()) {
                    throw new IllegalArgumentException("sourceEndpoint must be provided for each job");
                }
                Job j = new Job();
                j.setJobId(r.getJobId());
                j.setSourceEndpoint(r.getSourceEndpoint());
                j.setScheduledAt(r.getScheduledAt());
                j.setState("SCHEDULED");
                jobs.add(j);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    jobs
            );

            List<UUID> ids = idsFuture.get();
            BulkCreateJobResponse resp = new BulkCreateJobResponse();
            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) technicalIds.add(id.toString());
            }
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk create job request: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating jobs bulk", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating jobs bulk", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating jobs bulk", e);
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

    @Operation(summary = "Get all Jobs", description = "Retrieve all Jobs stored for this model/version.")
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

            ArrayNode arr = itemsFuture.get();
            List<JobResponse> responses = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    Job job = objectMapper.treeToValue(node, Job.class);
                    JobResponse resp = new JobResponse();
                    // technicalId may not be present in entity node; leave null if absent
                    resp.setJobId(job.getJobId());
                    resp.setScheduledAt(job.getScheduledAt());
                    resp.setStartedAt(job.getStartedAt());
                    resp.setFinishedAt(job.getFinishedAt());
                    resp.setState(job.getState());
                    resp.setRecordsFetchedCount(job.getRecordsFetchedCount());
                    resp.setErrorDetails(job.getErrorDetails());
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving all jobs", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all jobs", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving all jobs", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Filter Jobs", description = "Filter Jobs by a simple field-based condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<List<JobResponse>> filterJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Filter request", required = true,
                    content = @Content(schema = @Schema(implementation = FilterRequest.class)))
            @Valid @RequestBody FilterRequest request) {
        try {
            if (request.getFieldName() == null || request.getFieldName().isBlank()) {
                throw new IllegalArgumentException("fieldName must be provided");
            }
            if (request.getOperator() == null || request.getOperator().isBlank()) {
                throw new IllegalArgumentException("operator must be provided");
            }
            // Build simple SearchConditionRequest
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$."
                                    + request.getFieldName(),
                            request.getOperator(),
                            request.getValue())
            );

            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredFuture.get();
            List<JobResponse> responses = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    Job job = objectMapper.treeToValue(node, Job.class);
                    JobResponse resp = new JobResponse();
                    resp.setJobId(job.getJobId());
                    resp.setScheduledAt(job.getScheduledAt());
                    resp.setStartedAt(job.getStartedAt());
                    resp.setFinishedAt(job.getFinishedAt());
                    resp.setState(job.getState());
                    resp.setRecordsFetchedCount(job.getRecordsFetchedCount());
                    resp.setErrorDetails(job.getErrorDetails());
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid filter request: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while filtering jobs", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering jobs", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while filtering jobs", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Job", description = "Update a Job by its technicalId (UUID). Returns the technicalId of the updated Job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<UpdateJobResponse> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateJobRequest.class)))
            @Valid @RequestBody UpdateJobRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            UUID techUUID = UUID.fromString(technicalId);

            Job job = new Job();
            // allow partial updates - only set provided fields
            if (request.getJobId() != null) job.setJobId(request.getJobId());
            if (request.getSourceEndpoint() != null) job.setSourceEndpoint(request.getSourceEndpoint());
            if (request.getScheduledAt() != null) job.setScheduledAt(request.getScheduledAt());
            if (request.getStartedAt() != null) job.setStartedAt(request.getStartedAt());
            if (request.getFinishedAt() != null) job.setFinishedAt(request.getFinishedAt());
            if (request.getState() != null) job.setState(request.getState());
            if (request.getRecordsFetchedCount() != null) job.setRecordsFetchedCount(request.getRecordsFetchedCount());
            if (request.getErrorDetails() != null) job.setErrorDetails(request.getErrorDetails());

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    techUUID,
                    job
            );

            UUID updatedId = updatedFuture.get();
            UpdateJobResponse resp = new UpdateJobResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update job request: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while updating job", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating job", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while updating job", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Job", description = "Delete a Job by its technicalId (UUID). Returns the technicalId of the deleted Job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<DeleteJobResponse> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            UUID techUUID = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    techUUID
            );

            UUID deletedId = deletedFuture.get();
            DeleteJobResponse resp = new DeleteJobResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete job request: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while deleting job", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting job", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while deleting job", e);
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
    @Schema(name = "BulkCreateJobRequest", description = "Bulk create jobs request")
    public static class BulkCreateJobRequest {
        @Schema(description = "List of jobs to create", required = true)
        private List<CreateJobRequest> jobs;
    }

    @Data
    @Schema(name = "BulkCreateJobResponse", description = "Response after bulk creating Jobs")
    public static class BulkCreateJobResponse {
        @Schema(description = "List of technical identifiers (UUIDs) of the persisted jobs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "FilterRequest", description = "Simple filter request for Jobs")
    public static class FilterRequest {
        @Schema(description = "Field name to filter on (without JSONPath prefix)", example = "state", required = true)
        private String fieldName;

        @Schema(description = "Operator to use (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", example = "EQUALS", required = true)
        private String operator;

        @Schema(description = "Value to compare against", example = "SCHEDULED")
        private String value;
    }

    @Data
    @Schema(name = "UpdateJobRequest", description = "Payload to update a Job (partial update supported)")
    public static class UpdateJobRequest {
        @Schema(description = "Business job identifier (external)", example = "job-20250827-001")
        private String jobId;

        @Schema(description = "Source API endpoint URL")
        private String sourceEndpoint;

        @Schema(description = "When ingestion is scheduled (ISO datetime)")
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

    @Data
    @Schema(name = "UpdateJobResponse", description = "Response after updating a Job")
    public static class UpdateJobResponse {
        @Schema(description = "Technical identifier (UUID) of the updated job", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteJobResponse", description = "Response after deleting a Job")
    public static class DeleteJobResponse {
        @Schema(description = "Technical identifier (UUID) of the deleted job", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
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