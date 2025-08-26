package com.java_template.application.controller.commentanalysisjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "CommentAnalysisJob", description = "API for CommentAnalysisJob entity (version 1)")
@RequiredArgsConstructor
public class CommentAnalysisJobController {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisJobController.class);

    private final EntityService entityService;

    @Operation(summary = "Create CommentAnalysisJob", description = "Create a new CommentAnalysisJob and trigger processing. Returns technicalId only.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommentAnalysisJobController.TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true,
                content = @Content(schema = @Schema(implementation = CreateCommentAnalysisJobRequest.class)))
            @RequestBody CreateCommentAnalysisJobRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            CommentAnalysisJob job = new CommentAnalysisJob();

            // Map fields from request to entity
            if (request.post_id != null) {
                job.setPostId(String.valueOf(request.post_id));
            } else if (request.postId != null) { // fallback if camelCase used
                job.setPostId(String.valueOf(request.postId));
            } else {
                job.setPostId(null);
            }
            job.setRecipientEmail(request.recipient_email);
            job.setSchedule(request.schedule);
            // set requestedAt to now if not provided (basic request formatting)
            job.setRequestedAt(request.requested_at != null ? request.requested_at : Instant.now().toString());

            if (request.metrics_config != null && request.metrics_config.metrics != null) {
                CommentAnalysisJob.MetricsConfig mc = new CommentAnalysisJob.MetricsConfig();
                mc.setMetrics(request.metrics_config.metrics);
                job.setMetricsConfig(mc);
            }

            // Proxy to EntityService
            java.util.concurrent.CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    CommentAnalysisJob.ENTITY_NAME,
                    String.valueOf(CommentAnalysisJob.ENTITY_VERSION),
                    job
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = technicalId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createJob", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during createJob", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get CommentAnalysisJob by technicalId", description = "Retrieve a CommentAnalysisJob by its technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            java.util.concurrent.CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    CommentAnalysisJob.ENTITY_NAME,
                    String.valueOf(CommentAnalysisJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getJobById: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getJobById", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during getJobById", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get CommentAnalysisJobs", description = "Retrieve all CommentAnalysisJobs or filter by postId and/or status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CommentAnalysisJobController.JobResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = {})
    public ResponseEntity<?> getJobs(
            @Parameter(description = "Filter by postId (numeric)") @RequestParam(name = "postId", required = false) Integer postId,
            @Parameter(description = "Filter by status") @RequestParam(name = "status", required = false) String status
    ) {
        try {
            if (postId == null && (status == null || status.isBlank())) {
                java.util.concurrent.CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        CommentAnalysisJob.ENTITY_NAME,
                        String.valueOf(CommentAnalysisJob.ENTITY_VERSION)
                );
                ArrayNode items = itemsFuture.get();
                return ResponseEntity.ok(items);
            } else {
                List<Condition> conditions = new ArrayList<>();
                if (postId != null) {
                    // Field names correspond to entity JSON (postId)
                    conditions.add(Condition.of("$.postId", "EQUALS", String.valueOf(postId)));
                }
                if (status != null && !status.isBlank()) {
                    conditions.add(Condition.of("$.status", "EQUALS", status));
                }
                // Build SearchConditionRequest
                SearchConditionRequest conditionGroup = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                java.util.concurrent.CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                        CommentAnalysisJob.ENTITY_NAME,
                        String.valueOf(CommentAnalysisJob.ENTITY_VERSION),
                        conditionGroup,
                        true
                );
                ArrayNode items = filteredFuture.get();
                return ResponseEntity.ok(items);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getJobs: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getJobs", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during getJobs", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update CommentAnalysisJob", description = "Update an existing CommentAnalysisJob by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommentAnalysisJobController.TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update payload", required = true,
                content = @Content(schema = @Schema(implementation = CreateCommentAnalysisJobRequest.class)))
            @RequestBody CreateCommentAnalysisJobRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            CommentAnalysisJob job = new CommentAnalysisJob();
            if (request.post_id != null) {
                job.setPostId(String.valueOf(request.post_id));
            } else if (request.postId != null) {
                job.setPostId(String.valueOf(request.postId));
            }
            job.setRecipientEmail(request.recipient_email);
            job.setSchedule(request.schedule);
            job.setRequestedAt(request.requested_at);
            job.setCompletedAt(request.completed_at);
            job.setStatus(request.status);

            if (request.metrics_config != null && request.metrics_config.metrics != null) {
                CommentAnalysisJob.MetricsConfig mc = new CommentAnalysisJob.MetricsConfig();
                mc.setMetrics(request.metrics_config.metrics);
                job.setMetricsConfig(mc);
            }

            java.util.concurrent.CompletableFuture<java.util.UUID> updatedFuture = entityService.updateItem(
                    CommentAnalysisJob.ENTITY_NAME,
                    String.valueOf(CommentAnalysisJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    job
            );
            UUID updatedId = updatedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = updatedId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updateJob", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during updateJob", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete CommentAnalysisJob", description = "Delete a CommentAnalysisJob by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommentAnalysisJobController.TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            java.util.concurrent.CompletableFuture<java.util.UUID> deletedFuture = entityService.deleteItem(
                    CommentAnalysisJob.ENTITY_NAME,
                    String.valueOf(CommentAnalysisJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = deletedId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for deleteJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deleteJob", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during deleteJob", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // ----------- Static DTO classes for requests/responses ------------

    @Data
    @Schema(name = "CreateCommentAnalysisJobRequest", description = "Request to create a CommentAnalysisJob")
    public static class CreateCommentAnalysisJobRequest {
        @Schema(description = "Post identifier (numeric)", example = "1")
        public Integer post_id;

        // alternative camelCase if clients send it that way
        public Integer postId;

        @Schema(description = "Recipient email", example = "ops@example.com")
        public String recipient_email;

        @Schema(description = "Schedule (e.g., immediate)", example = "immediate")
        public String schedule;

        @Schema(description = "Requested at ISO-8601 timestamp", example = "2025-08-26T12:00:00Z")
        public String requested_at;

        @Schema(description = "Completed at ISO-8601 timestamp", example = "2025-08-26T12:01:00Z")
        public String completed_at;

        @Schema(description = "Job status", example = "CREATED")
        public String status;

        @Schema(description = "Metrics configuration")
        public MetricsConfigDTO metrics_config;

        @Data
        @Schema(name = "MetricsConfig", description = "Metrics configuration")
        public static class MetricsConfigDTO {
            @Schema(description = "List of metrics", example = "[\"count\",\"avg_length_words\"]")
            public List<String> metrics;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        public String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Representation of CommentAnalysisJob as returned by GET")
    public static class JobResponse {
        @Schema(description = "Technical ID of the job")
        public String technicalId;

        @Schema(description = "Post identifier (numeric)")
        public Integer post_id;

        @Schema(description = "Recipient email")
        public String recipient_email;

        @Schema(description = "Current job status")
        public String status;

        @Schema(description = "Requested at ISO-8601")
        public String requested_at;

        @Schema(description = "Completed at ISO-8601")
        public String completed_at;
    }
}