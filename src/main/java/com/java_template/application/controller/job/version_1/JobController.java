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
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Job", description = "API for Job entity (version 1)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Persist a new Job entity. Returns technicalId (UUID) of created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload")
            @RequestBody CreateJobRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Job job = new Job();
            job.setJobId(request.getJobId());
            job.setSourceEndpoint(request.getSourceEndpoint());
            job.setSchedule(request.getSchedule());
            // Default initial state per workflow; minimal required for entity validity
            job.setState(request.getState() != null ? request.getState() : "SCHEDULED");
            job.setRetryCount(request.getRetryCount() != null ? request.getRetryCount() : 0);
            if (request.getTriggeredAt() != null) job.setTriggeredAt(request.getTriggeredAt());
            if (request.getStartedAt() != null) job.setStartedAt(request.getStartedAt());
            if (request.getFinishedAt() != null) job.setFinishedAt(request.getFinishedAt());
            if (request.getResultSummary() != null) job.setResultSummary(request.getResultSummary());
            if (request.getErrorDetails() != null) job.setErrorDetails(request.getErrorDetails());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID createdId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(createdId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while creating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job entity by its technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            JobResponse resp = mapObjectNodeToJobResponse(node);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while retrieving job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Jobs", description = "Retrieve all Job entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<JobResponse> results = new ArrayList<>();
            if (array != null) {
                for (JsonNode n : array) {
                    if (n instanceof ObjectNode) {
                        results.add(mapObjectNodeToJobResponse((ObjectNode) n));
                    }
                }
            }
            return ResponseEntity.ok(results);
        } catch (ExecutionException e) {
            logger.error("ExecutionException while retrieving all jobs", e);
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while retrieving all jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Job", description = "Update an existing Job entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update payload")
            @RequestBody UpdateJobRequest request
    ) {
        try {
            if (technicalId == null) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Job job = new Job();
            // Populate only provided fields; ensure required fields exist for entity validity
            job.setJobId(request.getJobId());
            job.setSourceEndpoint(request.getSourceEndpoint());
            job.setSchedule(request.getSchedule());
            job.setState(request.getState());
            job.setTriggeredAt(request.getTriggeredAt());
            job.setStartedAt(request.getStartedAt());
            job.setFinishedAt(request.getFinishedAt());
            job.setResultSummary(request.getResultSummary());
            job.setErrorDetails(request.getErrorDetails());
            job.setRetryCount(request.getRetryCount());

            CompletableFuture<UUID> updated = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    job
            );
            UUID updId = updated.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid update request for job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while updating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Job", description = "Delete a Job entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID delId = deleted.get();
            return ResponseEntity.ok(new TechnicalIdResponse(delId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delete request for job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while deleting job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter Jobs", description = "Filter Job entities by a search condition. Uses SearchConditionRequest.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> filterJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload")
            @RequestBody SearchConditionRequest conditionRequest
    ) {
        try {
            if (conditionRequest == null) throw new IllegalArgumentException("conditionRequest is required");
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    conditionRequest,
                    true
            );
            ArrayNode array = filteredFuture.get();
            List<JobResponse> results = new ArrayList<>();
            if (array != null) {
                for (JsonNode n : array) {
                    if (n instanceof ObjectNode) {
                        results.add(mapObjectNodeToJobResponse((ObjectNode) n));
                    }
                }
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid filter request for jobs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            logger.error("ExecutionException while filtering jobs", e);
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while filtering jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private JobResponse mapObjectNodeToJobResponse(ObjectNode node) {
        JobResponse r = new JobResponse();
        r.setJobId(getTextOrNull(node, "jobId"));
        r.setSourceEndpoint(getTextOrNull(node, "sourceEndpoint"));
        r.setSchedule(getTextOrNull(node, "schedule"));
        r.setState(getTextOrNull(node, "state"));
        r.setTriggeredAt(getTextOrNull(node, "triggeredAt"));
        r.setStartedAt(getTextOrNull(node, "startedAt"));
        r.setFinishedAt(getTextOrNull(node, "finishedAt"));
        r.setResultSummary(getTextOrNull(node, "resultSummary"));
        r.setErrorDetails(getTextOrNull(node, "errorDetails"));
        if (node.has("retryCount") && node.get("retryCount").isNumber()) {
            r.setRetryCount(node.get("retryCount").asInt());
        } else {
            r.setRetryCount(null);
        }
        return r;
    }

    private String getTextOrNull(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        return n.asText(null);
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateJobRequest", description = "Request to create a Job")
    public static class CreateJobRequest {
        @Schema(description = "Human-readable job identifier", example = "daily_nobel_ingest")
        private String jobId;

        @Schema(description = "Source endpoint URL", example = "https://example.com/api")
        private String sourceEndpoint;

        @Schema(description = "Cron or schedule expression", example = "0 0 2 * * ?")
        private String schedule;

        @Schema(description = "Number of automatic retries attempted", example = "3")
        private Integer retryCount;

        @Schema(description = "Optional initial state (defaults to SCHEDULED)")
        private String state;

        @Schema(description = "When job run was triggered (ISO datetime)", example = "2025-08-20T02:00:00Z")
        private String triggeredAt;

        @Schema(description = "When ingestion started (ISO datetime)", example = "2025-08-20T02:01:00Z")
        private String startedAt;

        @Schema(description = "When ingestion finished (ISO datetime)", example = "2025-08-20T02:10:00Z")
        private String finishedAt;

        @Schema(description = "Summary of results")
        private String resultSummary;

        @Schema(description = "Error details if any")
        private String errorDetails;
    }

    @Data
    @Schema(name = "UpdateJobRequest", description = "Request to update a Job (partial fields allowed)")
    public static class UpdateJobRequest {
        @Schema(description = "Human-readable job identifier", example = "daily_nobel_ingest")
        private String jobId;

        @Schema(description = "Source endpoint URL", example = "https://example.com/api")
        private String sourceEndpoint;

        @Schema(description = "Cron or schedule expression", example = "0 0 2 * * ?")
        private String schedule;

        @Schema(description = "Job state", example = "SCHEDULED")
        private String state;

        @Schema(description = "When job run was triggered (ISO datetime)", example = "2025-08-20T02:00:00Z")
        private String triggeredAt;

        @Schema(description = "When ingestion started (ISO datetime)", example = "2025-08-20T02:01:00Z")
        private String startedAt;

        @Schema(description = "When ingestion finished (ISO datetime)", example = "2025-08-20T02:10:00Z")
        private String finishedAt;

        @Schema(description = "Summary of results")
        private String resultSummary;

        @Schema(description = "Error details if any")
        private String errorDetails;

        @Schema(description = "Number of automatic retries attempted", example = "3")
        private Integer retryCount;
    }

    @Data
    @Schema(name = "JobResponse", description = "Representation of Job entity returned by API")
    public static class JobResponse {
        @Schema(description = "Human-readable job identifier", example = "daily_nobel_ingest")
        private String jobId;

        @Schema(description = "Source endpoint URL", example = "https://example.com/api")
        private String sourceEndpoint;

        @Schema(description = "Cron or schedule expression", example = "0 0 2 * * ?")
        private String schedule;

        @Schema(description = "Job state", example = "SCHEDULED")
        private String state;

        @Schema(description = "When job run was triggered (ISO datetime)")
        private String triggeredAt;

        @Schema(description = "When ingestion started (ISO datetime)")
        private String startedAt;

        @Schema(description = "When ingestion finished (ISO datetime)")
        private String finishedAt;

        @Schema(description = "Summary of results")
        private String resultSummary;

        @Schema(description = "Error details if any")
        private String errorDetails;

        @Schema(description = "Number of automatic retries attempted", example = "0")
        private Integer retryCount;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id of created/modified entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}