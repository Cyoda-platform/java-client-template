package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.service.EntityService;
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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Job Controller", description = "Controller for Job entity (version 1). Proxy to EntityService only.")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public JobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Job", description = "Persist a new Job entity. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation request", required = true,
            content = @Content(schema = @Schema(implementation = JobRequest.class))) @RequestBody JobRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Job job = new Job();
            job.setId(request.getId());
            job.setSourceUrl(request.getSourceUrl());
            job.setSchedule(request.getSchedule());
            // Ensure required fields for entity validity are present. Controller must not implement business logic.
            // Provide sensible defaults to satisfy entity validation.
            job.setState(request.getState() != null ? request.getState() : "SCHEDULED");
            job.setProcessedCount(request.getProcessedCount() != null ? request.getProcessedCount() : 0);
            job.setFailedCount(request.getFailedCount() != null ? request.getFailedCount() : 0);
            job.setErrorSummary(request.getErrorSummary());
            job.setStartedAt(request.getStartedAt());
            job.setFinishedAt(request.getFinishedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    Job.ENTITY_VERSION,
                    job
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a single Job by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJobById(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                        @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            JsonNode data = dataPayload.getData();
            JobResponse response = objectMapper.treeToValue(data, JobResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Jobs", description = "Retrieve list of Jobs. This is a simple proxy to EntityService.getItems")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listJobs() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    Job.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<JobResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        JobResponse resp = objectMapper.treeToValue(payload.getData(), JobResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while listing jobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while listing jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Job", description = "Update an existing Job by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                       @PathVariable String technicalId,
                                       @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update request", required = true,
                                               content = @Content(schema = @Schema(implementation = JobRequest.class))) @RequestBody JobRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Job job = new Job();
            job.setId(request.getId());
            job.setSourceUrl(request.getSourceUrl());
            job.setSchedule(request.getSchedule());
            job.setState(request.getState());
            job.setProcessedCount(request.getProcessedCount());
            job.setFailedCount(request.getFailedCount());
            job.setErrorSummary(request.getErrorSummary());
            job.setStartedAt(request.getStartedAt());
            job.setFinishedAt(request.getFinishedAt());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    UUID.fromString(technicalId),
                    job
            );
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while updating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Job", description = "Delete a Job by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                       @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while deleting job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // DTOs

    @Data
    public static class JobRequest {
        @Schema(description = "Business id of the job", example = "job-001")
        private String id;
        @Schema(description = "Cron schedule or manual", example = "0 0 * * *")
        private String schedule;
        @Schema(description = "Source URL to ingest", example = "https://.../records")
        private String sourceUrl;
        @Schema(description = "State of the job", example = "SCHEDULED")
        private String state;
        @Schema(description = "Number of processed records", example = "0")
        private Integer processedCount;
        @Schema(description = "Number of failed records", example = "0")
        private Integer failedCount;
        @Schema(description = "Short error summary", example = "network error")
        private String errorSummary;
        @Schema(description = "ISO-8601 start timestamp", example = "2025-08-01T12:00:00Z")
        private String startedAt;
        @Schema(description = "ISO-8601 finished timestamp", example = "2025-08-01T12:10:00Z")
        private String finishedAt;
    }

    @Data
    public static class JobResponse {
        @Schema(description = "Business id of the job", example = "job-001")
        private String id;
        @Schema(description = "Cron schedule or manual", example = "0 0 * * *")
        private String schedule;
        @Schema(description = "Source URL to ingest", example = "https://.../records")
        private String sourceUrl;
        @Schema(description = "State of the job", example = "SCHEDULED")
        private String state;
        @Schema(description = "Number of processed records", example = "0")
        private Integer processedCount;
        @Schema(description = "Number of failed records", example = "0")
        private Integer failedCount;
        @Schema(description = "Short error summary", example = "network error")
        private String errorSummary;
        @Schema(description = "ISO-8601 start timestamp", example = "2025-08-01T12:00:00Z")
        private String startedAt;
        @Schema(description = "ISO-8601 finished timestamp", example = "2025-08-01T12:10:00Z")
        private String finishedAt;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier returned by the system", example = "T_JOB_0001")
        private String technicalId;
    }
}