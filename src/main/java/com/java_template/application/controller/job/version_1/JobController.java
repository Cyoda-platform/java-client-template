package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Job Controller", description = "Controller for Job entity (version 1). Proxy to EntityService only.")
@RequiredArgsConstructor
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Job", description = "Create a new Job. This controller only proxies to the EntityService. Business logic is handled by workflows.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true, content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody CreateJobRequest request) {
        try {
            // Basic request validation
            if (request == null) {
                logger.warn("CreateJobRequest is null");
                return ResponseEntity.badRequest().body("Request body is required");
            }
            if (request.getType() == null || request.getType().isBlank()) {
                return ResponseEntity.badRequest().body("type is required");
            }
            if (request.getPayload() == null || request.getPayload().getApiUrl() == null || request.getPayload().getApiUrl().isBlank()) {
                return ResponseEntity.badRequest().body("payload.apiUrl is required");
            }
            if (request.getPayload().getRows() == null || request.getPayload().getRows() < 0) {
                return ResponseEntity.badRequest().body("payload.rows must be non-negative");
            }

            // Build Job entity (minimal fields required to satisfy entity validation)
            Job job = new Job();
            job.setType(request.getType());
            job.setStatus("PENDING"); // initial state expected by workflows; set as minimal required field
            job.setCreatedAt(Instant.now().toString());
            job.setAttemptCount(0);
            Job.Payload payload = new Job.Payload();
            payload.setApiUrl(request.getPayload().getApiUrl());
            payload.setRows(request.getPayload().getRows());
            job.setPayload(payload);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();

            CreateJobResponse resp = new CreateJobResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument while creating job", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
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

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job by its technicalId. This controller only proxies to the EntityService.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
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
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.badRequest().body("technicalId is required");
            }

            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }

            // Map ObjectNode to Job instance (entity fields)
            Job job = objectMapper.treeToValue(node, Job.class);

            JobResponse resp = new JobResponse();
            resp.setTechnicalId(technicalId);
            if (job != null) {
                resp.setType(job.getType());
                resp.setStatus(job.getStatus());
                resp.setCreatedAt(job.getCreatedAt());
                resp.setStartedAt(job.getStartedAt());
                resp.setCompletedAt(job.getCompletedAt());
                resp.setAttemptCount(job.getAttemptCount());
                resp.setResultRef(job.getResultRef());
                if (job.getPayload() != null) {
                    PayloadDto p = new PayloadDto();
                    p.setApiUrl(job.getPayload().getApiUrl());
                    p.setRows(job.getPayload().getRows());
                    resp.setPayload(p);
                }
            } else {
                // If mapping fails but node exists, include raw node (attempt to extract fields)
                if (node.has("type")) resp.setType(node.get("type").asText(null));
                if (node.has("status")) resp.setStatus(node.get("status").asText(null));
                if (node.has("createdAt")) resp.setCreatedAt(node.get("createdAt").asText(null));
                if (node.has("startedAt")) resp.setStartedAt(node.get("startedAt").asText(null));
                if (node.has("completedAt")) resp.setCompletedAt(node.get("completedAt").asText(null));
                if (node.has("attemptCount")) resp.setAttemptCount(node.get("attemptCount").isNumber() ? node.get("attemptCount").intValue() : null);
                if (node.has("resultRef")) resp.setResultRef(node.get("resultRef").asText(null));
                if (node.has("payload") && node.get("payload").isObject()) {
                    ObjectNode payloadNode = (ObjectNode) node.get("payload");
                    PayloadDto p = new PayloadDto();
                    if (payloadNode.has("apiUrl")) p.setApiUrl(payloadNode.get("apiUrl").asText(null));
                    if (payloadNode.has("rows")) p.setRows(payloadNode.get("rows").isNumber() ? payloadNode.get("rows").intValue() : null);
                    resp.setPayload(p);
                }
            }

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument while retrieving job", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
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

    // --- DTOs ---

    @Data
    @Schema(name = "CreateJobRequest", description = "Request to create a Job")
    public static class CreateJobRequest {
        @Schema(description = "Job type (e.g. INGEST, TRANSFORM, NOTIFY)", required = true, example = "INGEST")
        private String type;

        @Schema(description = "Job payload", required = true)
        private PayloadDto payload;
    }

    @Data
    @Schema(name = "PayloadDto", description = "Job payload")
    public static class PayloadDto {
        @Schema(description = "API URL or dataset pointer", required = true, example = "https://example.com/dataset?id=1")
        private String apiUrl;

        @Schema(description = "Number of rows to fetch", example = "50")
        private Integer rows;
    }

    @Data
    @Schema(name = "CreateJobResponse", description = "Response after creating a Job")
    public static class CreateJobResponse {
        @Schema(description = "Technical id generated by the datastore", example = "job-123e4567")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job representation returned by GET")
    public static class JobResponse {
        @Schema(description = "Technical id of the job", example = "job-123e4567")
        private String technicalId;

        @Schema(description = "Job type", example = "INGEST")
        private String type;

        @Schema(description = "Job status", example = "RUNNING")
        private String status;

        @Schema(description = "Creation timestamp (ISO8601)", example = "2025-08-26T12:00:00Z")
        private String createdAt;

        @Schema(description = "Start timestamp (ISO8601)", example = "2025-08-26T12:00:05Z")
        private String startedAt;

        @Schema(description = "Completion timestamp (ISO8601)", example = "2025-08-26T12:01:00Z")
        private String completedAt;

        @Schema(description = "Attempt count", example = "1")
        private Integer attemptCount;

        @Schema(description = "Optional reference to result entity")
        private String resultRef;

        @Schema(description = "Job payload")
        private PayloadDto payload;
    }
}