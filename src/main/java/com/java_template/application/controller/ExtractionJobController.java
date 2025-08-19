package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
// Note: avoid importing io.swagger.v3.oas.annotations.parameters.RequestBody to prevent collision
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/extraction-jobs")
@Tag(name = "ExtractionJob Controller", description = "Proxy controller for ExtractionJob entity operations")
public class ExtractionJobController {
    private static final Logger logger = LoggerFactory.getLogger(ExtractionJobController.class);

    private final EntityService entityService;

    public ExtractionJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Extraction Job", description = "Create a new extraction job. Idempotency-Key header supported by workflows.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateExtractionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createExtractionJob(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Extraction job payload")
                                                 @org.springframework.web.bind.annotation.RequestBody CreateExtractionJobRequest request) {
        try {
            ExtractionJob job = new ExtractionJob();
            job.setJobId(request.getJobId());
            job.setSchedule(request.getSchedule());
            job.setSourceUrl(request.getSourceUrl());
            job.setParameters(request.getParameters());
            job.setRecipients(request.getRecipients());
            job.setReportTemplateId(request.getReportTemplateId());
            job.setCreatedAt(Instant.now().toString());
            job.setStatus("PENDING");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ExtractionJob.ENTITY_NAME,
                    String.valueOf(ExtractionJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();

            CreateExtractionJobResponse resp = new CreateExtractionJobResponse();
            resp.setTechnicalId(technicalId.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.LOCATION, "/extraction-jobs/" + technicalId.toString());

            // If idempotency header was provided the service may return existing id; return 200 in that case
            return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for creating ExtractionJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_REQUEST", e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERRUPTED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while creating ExtractionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "Get Extraction Job", description = "Retrieve an extraction job by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ExtractionJobResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getExtractionJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ExtractionJob.ENTITY_NAME,
                    String.valueOf(ExtractionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            if (node == null) throw new NoSuchElementException("ExtractionJob not found");
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for getExtractionJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERRUPTED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while fetching ExtractionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "Start Extraction Job", description = "Manually start a job (dryRun optional)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @PostMapping(value = "/{technicalId}/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> startJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Start options") @org.springframework.web.bind.annotation.RequestBody(required = false) StartJobRequest request) {
        try {
            // Proxy: ensure job exists
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ExtractionJob.ENTITY_NAME,
                    String.valueOf(ExtractionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) throw new NoSuchElementException("ExtractionJob not found");

            // Business logic handled by workflows; controller just acknowledges
            return ResponseEntity.accepted().body(Map.of("technicalId", technicalId, "status", "START_REQUESTED"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for startJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERRUPTED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while starting ExtractionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "Cancel Extraction Job", description = "Request cancellation of a running job")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @PostMapping(value = "/{technicalId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancelJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ExtractionJob.ENTITY_NAME,
                    String.valueOf(ExtractionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) throw new NoSuchElementException("ExtractionJob not found");

            return ResponseEntity.ok(Map.of("technicalId", technicalId, "status", "CANCEL_REQUESTED"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for cancelJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERRUPTED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while cancelling ExtractionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "Rerun Extraction Job", description = "Trigger a rerun of the job with optional overrides")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @PostMapping(value = "/{technicalId}/rerun", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rerunJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Rerun options") @org.springframework.web.bind.annotation.RequestBody(required = false) RerunJobRequest request) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ExtractionJob.ENTITY_NAME,
                    String.valueOf(ExtractionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) throw new NoSuchElementException("ExtractionJob not found");

            return ResponseEntity.accepted().body(Map.of("technicalId", technicalId, "status", "RERUN_REQUESTED"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for rerunJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERRUPTED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while rerunning ExtractionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
        }
    }

    private ResponseEntity<Object> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", cause.getMessage()));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Bad request", cause);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", cause.getMessage()));
        } else {
            logger.error("Execution error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("EXECUTION_ERROR", cause != null ? cause.getMessage() : e.getMessage()));
        }
    }

    private Map<String, Object> errorBody(String code, String message) {
        return Map.of("errorCode", code, "message", message != null ? message : "");
    }

    @Data
    @Schema(name = "CreateExtractionJobRequest")
    public static class CreateExtractionJobRequest {
        @Schema(description = "Business job id", required = true)
        private String jobId;
        @Schema(description = "Schedule (cron or human readable)", required = true)
        private String schedule;
        @Schema(description = "Timezone e.g. Europe/Moscow")
        private String timezone;
        @Schema(description = "Start immediately")
        private Boolean immediateStart;
        @Schema(description = "Source base URL", required = true)
        private String sourceUrl;
        @Schema(description = "Parameters object")
        private Map<String, Object> parameters;
        @Schema(description = "Recipients list", required = true)
        private List<String> recipients;
        @Schema(description = "Report template id", required = true)
        private String reportTemplateId;
        @Schema(description = "Retention policy")
        private Map<String, Object> retentionPolicy;
        @Schema(description = "Optional idempotency key")
        private String idempotencyKey;
    }

    @Data
    @Schema(name = "CreateExtractionJobResponse")
    public static class CreateExtractionJobResponse {
        @Schema(description = "Technical id returned by the system")
        private String technicalId;
    }

    @Data
    @Schema(name = "ExtractionJobResponse")
    public static class ExtractionJobResponse {
        private String jobId;
        private String technicalId;
        private String schedule;
        private String timezone;
        private String status;
        private String lastRunAt;
        private String lastAttemptAt;
        private String createdAt;
        private Map<String, Object> progress;
        private String failureReason;
    }

    @Data
    @Schema(name = "StartJobRequest")
    public static class StartJobRequest {
        private Boolean dryRun;
    }

    @Data
    @Schema(name = "RerunJobRequest")
    public static class RerunJobRequest {
        private Map<String, Object> parameters;
        private Boolean immediateStart;
    }
}