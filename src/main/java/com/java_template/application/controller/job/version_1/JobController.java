package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.job.version_1.Job;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/v1/job")
@Tag(name = "Job", description = "APIs for Job entity (version 1) - controller is a thin proxy to EntityService")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a Job entity. This will persist the job and trigger workflows. Returns the technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job create request", required = true,
                    content = @Content(schema = @Schema(implementation = JobCreateRequest.class)))
            @RequestBody JobCreateRequest request) {
        try {
            // Basic format validation
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getScheduleType() == null || request.getScheduleType().trim().isEmpty()) {
                throw new IllegalArgumentException("scheduleType is required");
            }
            // Map request to entity (no business logic here)
            Job jobEntity = objectMapper.convertValue(request, Job.class);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    jobEntity
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse response = new TechnicalIdResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createJob request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in createJob", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve Job entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    uuid
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(404).body("Job not found");
            }
            // Convert ObjectNode to response DTO
            JobResponse response = objectMapper.treeToValue(node, JobResponse.class);
            // Ensure technicalId present in response
            if (response.getTechnicalId() == null) {
                response.setTechnicalId(technicalId);
            }
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getJobById request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJobById", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting job", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getJobById", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "List all Jobs", description = "Retrieve all Job entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            if (arrayNode == null) {
                return ResponseEntity.ok(List.of());
            }
            List<JobResponse> list = objectMapper.convertValue(arrayNode, new TypeReference<List<JobResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in listJobs", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing jobs", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in listJobs", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    // Static DTO classes used for request/response payloads
    @Data
    @Schema(name = "JobCreateRequest", description = "Request payload to create a Job")
    public static class JobCreateRequest {
        @Schema(description = "Human name of the job", example = "Daily Laureates Ingest")
        private String name;

        @Schema(description = "Schedule type (one-time or recurring)", example = "recurring")
        private String scheduleType;

        @Schema(description = "Cron or human schedule descriptor", example = "0 0 * * *")
        private String scheduleSpec;

        @Schema(description = "Data source identifier", example = "dataset_nobel_laureates")
        private String sourceEndpoint;

        @Schema(description = "Whether the job is enabled", example = "true")
        private Boolean enabled;

        @Schema(description = "Optional initial status", example = "PENDING")
        private String status;

        @Schema(description = "Optional lastResultSummary")
        private String lastResultSummary;

        @Schema(description = "Optional lastRunTimestamp (ISO)", example = "2025-01-01T00:00:00Z")
        private String lastRunTimestamp;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "job_technical_123")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job entity response")
    public static class JobResponse {
        @Schema(description = "Technical ID of the entity", example = "job_technical_123")
        private String technicalId;

        @Schema(description = "Human name of the job", example = "Daily Laureates Ingest")
        private String name;

        @Schema(description = "Schedule type (one-time or recurring)", example = "recurring")
        private String scheduleType;

        @Schema(description = "Cron or human schedule descriptor", example = "0 0 * * *")
        private String scheduleSpec;

        @Schema(description = "Data source identifier", example = "dataset_nobel_laureates")
        private String sourceEndpoint;

        @Schema(description = "ISO timestamp of last run", example = "2025-01-01T00:00:00Z")
        private String lastRunTimestamp;

        @Schema(description = "Current job status", example = "PENDING")
        private String status;

        @Schema(description = "Job enabled flag", example = "true")
        private Boolean enabled;

        @Schema(description = "Summary of last run")
        private String lastResultSummary;
    }
}