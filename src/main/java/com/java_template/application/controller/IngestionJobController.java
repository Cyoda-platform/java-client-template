package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

@Tag(name = "IngestionJob API", description = "Operations for creating and retrieving ingestion jobs")
@RestController
@RequestMapping("/api/jobs")
@Validated
public class IngestionJobController {
    private static final Logger logger = LoggerFactory.getLogger(IngestionJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestionJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ingestion job", description = "Create an IngestionJob and start async processing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted", content = @Content(schema = @Schema(implementation = JobCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JobCreateResponse> createJob(@RequestBody JobCreateRequest request) {
        try {
            // Basic validation
            if (request.getPostId() == null || request.getPostId() <= 0) {
                throw new IllegalArgumentException("postId is required and must be positive");
            }
            if (request.getRequestedByEmail() == null || request.getRequestedByEmail().isBlank() || !request.getRequestedByEmail().contains("@")) {
                throw new IllegalArgumentException("requestedByEmail is required and must be a valid email");
            }
            if (request.getRecipients() == null) {
                throw new IllegalArgumentException("recipients must be provided (may be empty list)");
            }

            // Map request to entity
            IngestionJob job = new IngestionJob();
            job.setPostId(request.getPostId());
            job.setRequestedByEmail(request.getRequestedByEmail());
            job.setRecipients(request.getRecipients());
            job.setSchedule(request.getSchedule());
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();

            JobCreateResponse resp = new JobCreateResponse(technicalId.toString(), "PENDING");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation failed creating job: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get ingestion job", description = "Retrieve ingestion job details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobDetailsResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for getJob: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Retry ingestion job", description = "Trigger a retry for a failed ingestion job (admin) by re-submitting the existing entity to the service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted", content = @Content(schema = @Schema(implementation = RetryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping(value = "/{technicalId}/retry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RetryResponse> retryJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode existing = itemFuture.get();

            // Re-submit the same entity to updateItem which will trigger workflows as appropriate
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    existing
            );
            UUID updatedId = updatedFuture.get();

            RetryResponse resp = new RetryResponse(updatedId.toString(), "RETRY_SUBMITTED");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for retryJob: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrying job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrying job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().build();
        }
        logger.error("ExecutionException in IngestionJobController", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @Data
    public static class JobCreateRequest {
        @Schema(description = "Post id to ingest comments for", required = true, example = "1")
        private Integer postId;

        @Schema(description = "Email that requested the report / primary recipient", required = true, example = "owner@example.com")
        private String requestedByEmail;

        @Schema(description = "Additional email recipients", required = true)
        private java.util.List<String> recipients;

        @Schema(description = "Cron expression or RUN_ONCE", required = false, example = "RUN_ONCE")
        private String schedule;
    }

    @Data
    public static class JobCreateResponse {
        @Schema(description = "Technical id of created job")
        private String technicalId;
        @Schema(description = "Current state of the job")
        private String state;

        public JobCreateResponse(String technicalId, String state) {
            this.technicalId = technicalId;
            this.state = state;
        }
    }

    @Data
    public static class JobDetailsResponse {
        @Schema(description = "Technical id")
        private String technicalId;
        @Schema(description = "Post id")
        private Integer postId;
        @Schema(description = "State")
        private String state;
        @Schema(description = "Created timestamp")
        private String createdAt;
        @Schema(description = "Started timestamp")
        private String startedAt;
        @Schema(description = "Completed timestamp")
        private String completedAt;
        @Schema(description = "Result report technical id")
        private String resultReportTechnicalId;
        @Schema(description = "Status message")
        private String statusMessage;
    }

    @Data
    public static class RetryResponse {
        @Schema(description = "Technical id of retried job")
        private String technicalId;
        @Schema(description = "Retry status")
        private String status;

        public RetryResponse(String technicalId, String status) {
            this.technicalId = technicalId;
            this.status = status;
        }
    }
}
