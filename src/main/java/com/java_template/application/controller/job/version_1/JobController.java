package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getJobName() == null || request.getJobName().isBlank())
                throw new IllegalArgumentException("jobName is required");
            if (request.getSource() == null || request.getSource().isBlank())
                throw new IllegalArgumentException("source is required");
            if (request.getLocations() == null || request.getLocations().isEmpty())
                throw new IllegalArgumentException("locations must be provided");

            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setSource(request.getSource());
            job.setLocations(request.getLocations());
            job.setSchedule(request.getSchedule());
            job.setParameters(request.getParameters());
            job.setCreatedAt(Instant.now().toString());
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
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

            JobSummaryResponse resp = mapNodeToSummary(node, technicalId);

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

    @Operation(summary = "List Jobs", description = "Retrieve all jobs (summary view)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobSummaryResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            );

            ArrayNode arr = itemsFuture.get();
            List<JobSummaryResponse> results = new ArrayList<>();
            if (arr != null) {
                for (JsonNode n : arr) {
                    if (n != null && n.isObject()) {
                        results.add(mapNodeToSummary((ObjectNode) n, n.has("id") ? n.get("id").asText() : null));
                    }
                }
            }
            return ResponseEntity.ok(results);

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in listJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Bulk Create Jobs", description = "Create multiple Job entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkCreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createJobsBulk(
            @RequestBody(description = "Bulk create jobs request", required = true, content = @Content(schema = @Schema(implementation = BulkCreateJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody BulkCreateJobRequest request) {
        try {
            if (request == null || request.getJobs() == null || request.getJobs().isEmpty()) {
                throw new IllegalArgumentException("jobs list is required");
            }

            List<Job> jobs = new ArrayList<>();
            for (CreateJobRequest r : request.getJobs()) {
                if (r.getJobName() == null || r.getJobName().isBlank())
                    throw new IllegalArgumentException("jobName is required for each job");
                if (r.getSource() == null || r.getSource().isBlank())
                    throw new IllegalArgumentException("source is required for each job");
                if (r.getLocations() == null || r.getLocations().isEmpty())
                    throw new IllegalArgumentException("locations must be provided for each job");

                Job job = new Job();
                job.setJobName(r.getJobName());
                job.setSource(r.getSource());
                job.setLocations(r.getLocations());
                job.setSchedule(r.getSchedule());
                job.setParameters(r.getParameters());
                job.setCreatedAt(Instant.now().toString());
                job.setStatus("PENDING");
                jobs.add(job);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    jobs
            );

            List<UUID> ids = idsFuture.get();
            BulkCreateJobResponse resp = new BulkCreateJobResponse();
            List<String> stringIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) stringIds.add(u.toString());
            }
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJobsBulk: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJobsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk creating jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in createJobsBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Job", description = "Update a Job entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @RequestBody(description = "Update Job request", required = true, content = @Content(schema = @Schema(implementation = UpdateJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateJobRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID id = UUID.fromString(technicalId);

            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setSource(request.getSource());
            job.setLocations(request.getLocations());
            job.setSchedule(request.getSchedule());
            job.setParameters(request.getParameters());
            job.setStatus(request.getStatus());
            // preserve createdAt if client doesn't provide; controller shouldn't attempt deep business logic
            if (request.getCreatedAt() != null) job.setCreatedAt(request.getCreatedAt());

            CompletableFuture<UUID> updated = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id,
                    job
            );

            UUID updatedId = updated.get();
            UpdateJobResponse resp = new UpdateJobResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in updateJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Job", description = "Delete a Job entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );

            UUID delId = deleted.get();
            DeleteJobResponse resp = new DeleteJobResponse();
            resp.setTechnicalId(delId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Jobs", description = "Search jobs using a simple SearchConditionRequest")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobSummaryResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchJobs(
            @RequestBody(description = "Search request", required = true, content = @Content(schema = @Schema(implementation = SearchRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody SearchRequest request) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("condition is required");
            }

            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    request.getCondition(),
                    request.getInMemory() == null ? true : request.getInMemory()
            );

            ArrayNode arr = future.get();
            List<JobSummaryResponse> results = new ArrayList<>();
            if (arr != null) {
                for (JsonNode n : arr) {
                    if (n != null && n.isObject()) {
                        results.add(mapNodeToSummary((ObjectNode) n, n.has("id") ? n.get("id").asText() : null));
                    }
                }
            }
            return ResponseEntity.ok(results);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchJobs: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in searchJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Helper to map ObjectNode to JobSummaryResponse (keeps controller free of business rules)
    private JobSummaryResponse mapNodeToSummary(ObjectNode node, String fallbackId) {
        JobSummaryResponse resp = new JobSummaryResponse();
        if (node != null) {
            if (node.has("id")) resp.setTechnicalId(node.get("id").asText());
            else resp.setTechnicalId(fallbackId);

            if (node.has("jobName")) resp.setJobName(node.get("jobName").asText(null));
            if (node.has("status")) resp.setStatus(node.get("status").asText(null));
            if (node.has("createdAt")) resp.setCreatedAt(node.get("createdAt").asText(null));
            if (node.has("processedCount")) resp.setProcessedCount(node.get("processedCount").asInt(0));
            if (node.has("failedCount")) resp.setFailedCount(node.get("failedCount").asInt(0));
        } else {
            resp.setTechnicalId(fallbackId);
        }
        return resp;
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
        @Schema(description = "Technical ID of the job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
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

    @Data
    @Schema(name = "BulkCreateJobRequest", description = "Request to create multiple jobs")
    public static class BulkCreateJobRequest {
        @Schema(description = "List of jobs to create", required = true)
        private List<CreateJobRequest> jobs;
    }

    @Data
    @Schema(name = "BulkCreateJobResponse", description = "Response for bulk create")
    public static class BulkCreateJobResponse {
        @Schema(description = "List of technical IDs created")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "UpdateJobRequest", description = "Request payload to update a Job")
    public static class UpdateJobRequest {
        @Schema(description = "Human name for the ingestion job", example = "DailyGeoMetIngest")
        private String jobName;

        @Schema(description = "Data source", example = "MSC GeoMet")
        private String source;

        @Schema(description = "Location IDs to pull", example = "[\"LOC123\",\"LOC456\"]")
        private List<String> locations;

        @Schema(description = "Cron or cadence description", example = "every 15 minutes")
        private String schedule;

        @Schema(description = "Extra parameters", example = "{}")
        private Map<String, Object> parameters;

        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "ISO-8601 creation timestamp (optional)")
        private String createdAt;
    }

    @Data
    @Schema(name = "UpdateJobResponse", description = "Response after updating a Job")
    public static class UpdateJobResponse {
        @Schema(description = "Technical ID of the updated job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteJobResponse", description = "Response after deleting a Job")
    public static class DeleteJobResponse {
        @Schema(description = "Technical ID of the deleted job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "SearchRequest", description = "Request to search jobs by condition")
    public static class SearchRequest {
        @Schema(description = "Search condition group")
        private SearchConditionRequest condition;

        @Schema(description = "Whether to execute search in memory", example = "true")
        private Boolean inMemory = true;
    }
}