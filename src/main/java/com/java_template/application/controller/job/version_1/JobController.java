package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Job", description = "APIs for Job entity (version 1)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Creates a Job entity and returns the technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job payload", required = true,
                    content = @Content(schema = @Schema(implementation = JobCreateRequest.class)))
            @RequestBody JobCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getId() == null || request.getId().isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (request.getSchedule() == null || request.getSchedule().isBlank()) {
                throw new IllegalArgumentException("schedule is required");
            }

            Job job = new Job();
            job.setId(request.getId());
            job.setSchedule(request.getSchedule());
            // Controller remains a proxy - do not set workflow-managed fields.

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    Job.ENTITY_VERSION,
                    job
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId != null ? technicalId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create Job: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create: {}", cause.getMessage());
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during createJob", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createJob", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error during createJob", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieves a Job entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<JobResponse> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.notFound().build();
            }

            JsonNode dataNode = (JsonNode) dataPayload.getData();
            Job jobEntity = objectMapper.treeToValue(dataNode, Job.class);

            JobResponse resp = mapToResponse(jobEntity);

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get Job: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Job not found: {}", cause.getMessage());
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during get: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during getJobById", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getJobById", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error during getJobById", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "List Jobs", description = "Lists Job entities. Optional query parameter 'state' to filter by state.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<JobResponse>> listJobs(
            @Parameter(name = "state", description = "Optional state to filter jobs")
            @RequestParam(required = false) String state) {
        try {
            List<DataPayload> dataPayloads;
            if (state == null || state.isBlank()) {
                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                        Job.ENTITY_NAME,
                        Job.ENTITY_VERSION,
                        null, null, null
                );
                dataPayloads = itemsFuture.get();
            } else {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.state", "EQUALS", state)
                );
                CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                        Job.ENTITY_NAME,
                        Job.ENTITY_VERSION,
                        condition,
                        true
                );
                dataPayloads = filteredItemsFuture.get();
            }

            List<JobResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode node = (JsonNode) payload.getData();
                    Job jobEntity = objectMapper.treeToValue(node, Job.class);
                    responses.add(mapToResponse(jobEntity));
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to list Jobs: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during list: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during listJobs", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during listJobs", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error during listJobs", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Job", description = "Updates a Job entity by technicalId and returns the technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update payload", required = true,
                    content = @Content(schema = @Schema(implementation = JobUpdateRequest.class)))
            @RequestBody JobUpdateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Job job = new Job();
            // Copy fields if present (controller remains a proxy)
            if (request.getId() != null) job.setId(request.getId());
            if (request.getSchedule() != null) job.setSchedule(request.getSchedule());
            if (request.getState() != null) job.setState(request.getState());
            if (request.getStartedAt() != null) job.setStartedAt(request.getStartedAt());
            if (request.getFinishedAt() != null) job.setFinishedAt(request.getFinishedAt());
            if (request.getRecordsFetchedCount() != null) job.setRecordsFetchedCount(request.getRecordsFetchedCount());
            if (request.getRecordsProcessedCount() != null) job.setRecordsProcessedCount(request.getRecordsProcessedCount());
            if (request.getRecordsFailedCount() != null) job.setRecordsFailedCount(request.getRecordsFailedCount());
            if (request.getErrorSummary() != null) job.setErrorSummary(request.getErrorSummary());
            if (request.getSubscribersNotifiedCount() != null) job.setSubscribersNotifiedCount(request.getSubscribersNotifiedCount());

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    UUID.fromString(technicalId),
                    job
            );
            UUID updatedId = updatedFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId != null ? updatedId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update Job: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Job not found during update: {}", cause.getMessage());
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during update: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during updateJob", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateJob", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error during updateJob", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Job", description = "Deletes a Job entity by its technicalId and returns the technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId != null ? deletedId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete Job: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Job not found during delete: {}", cause.getMessage());
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during delete: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during deleteJob", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteJob", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error during deleteJob", ex);
            return ResponseEntity.status(500).build();
        }
    }

    private JobResponse mapToResponse(Job jobEntity) {
        JobResponse resp = new JobResponse();
        if (jobEntity == null) return resp;
        resp.setId(jobEntity.getId());
        resp.setState(jobEntity.getState());
        resp.setSchedule(jobEntity.getSchedule());
        resp.setStartedAt(jobEntity.getStartedAt());
        resp.setFinishedAt(jobEntity.getFinishedAt());
        resp.setRecordsFetchedCount(jobEntity.getRecordsFetchedCount());
        resp.setRecordsProcessedCount(jobEntity.getRecordsProcessedCount());
        resp.setRecordsFailedCount(jobEntity.getRecordsFailedCount());
        resp.setErrorSummary(jobEntity.getErrorSummary());
        resp.setSubscribersNotifiedCount(jobEntity.getSubscribersNotifiedCount());
        return resp;
    }

    // Static DTO classes for request/response payloads
    @Data
    @Schema(name = "JobCreateRequest", description = "Payload to create a Job")
    public static class JobCreateRequest {
        @Schema(description = "Business id for the job", example = "job-2025-08-01")
        private String id;

        @Schema(description = "Schedule descriptor (e.g. manual, daily)", example = "manual")
        private String schedule;
    }

    @Data
    @Schema(name = "JobUpdateRequest", description = "Payload to update a Job")
    public static class JobUpdateRequest {
        @Schema(description = "Business id for the job", example = "job-2025-08-01")
        private String id;

        @Schema(description = "Schedule descriptor", example = "manual")
        private String schedule;

        @Schema(description = "Current state of the job", example = "NOTIFIED_SUBSCRIBERS")
        private String state;

        @Schema(description = "ISO timestamp when job started", example = "2025-08-01T10:00:00Z")
        private String startedAt;

        @Schema(description = "ISO timestamp when job finished", example = "2025-08-01T10:00:10Z")
        private String finishedAt;

        @Schema(description = "Number of records fetched", example = "200")
        private Integer recordsFetchedCount;

        @Schema(description = "Number of records processed", example = "198")
        private Integer recordsProcessedCount;

        @Schema(description = "Number of records failed", example = "2")
        private Integer recordsFailedCount;

        @Schema(description = "Summary of errors", example = "2 invalid records")
        private String errorSummary;

        @Schema(description = "Number of subscribers notified", example = "5")
        private Integer subscribersNotifiedCount;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id returned by the system", example = "TID_JOB_12345")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job representation returned by the API")
    public static class JobResponse {
        @Schema(description = "Business id for the job", example = "job-2025-08-01")
        private String id;

        @Schema(description = "Schedule descriptor", example = "manual")
        private String schedule;

        @Schema(description = "Current state of the job", example = "NOTIFIED_SUBSCRIBERS")
        private String state;

        @Schema(description = "ISO timestamp when job started", example = "2025-08-01T10:00:00Z")
        private String startedAt;

        @Schema(description = "ISO timestamp when job finished", example = "2025-08-01T10:00:10Z")
        private String finishedAt;

        @Schema(description = "Number of records fetched", example = "200")
        private Integer recordsFetchedCount;

        @Schema(description = "Number of records processed", example = "198")
        private Integer recordsProcessedCount;

        @Schema(description = "Number of records failed", example = "2")
        private Integer recordsFailedCount;

        @Schema(description = "Summary of errors", example = "2 invalid records")
        private String errorSummary;

        @Schema(description = "Number of subscribers notified", example = "5")
        private Integer subscribersNotifiedCount;
    }
}