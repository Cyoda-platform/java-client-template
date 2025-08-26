package com.java_template.application.controller.batchjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "BatchJob Controller", description = "Proxy controller for BatchJob entity operations")
public class BatchJobController {

    private static final Logger logger = LoggerFactory.getLogger(BatchJobController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public BatchJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create BatchJob", description = "Create a BatchJob entity. Creating a BatchJob triggers the workflow processing.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateBatchJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createBatchJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "BatchJob creation payload",
            required = true,
            content = @Content(schema = @Schema(implementation = CreateBatchJobRequest.class))
    ) @RequestBody CreateBatchJobRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            BatchJob data = new BatchJob();
            data.setJobName(request.getJobName());
            data.setScheduleCron(request.getScheduleCron());
            data.setRunMonth(request.getRunMonth());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    data
            );

            UUID technicalId = idFuture.get();

            CreateBatchJobResponse response = new CreateBatchJobResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when creating BatchJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createBatchJob", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createBatchJob", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during createBatchJob", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get BatchJob by technicalId", description = "Retrieve a BatchJob entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getBatchJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(404).body("BatchJob not found");
            }

            BatchJobResponse resp = mapper.convertValue(node, BatchJobResponse.class);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when fetching BatchJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getBatchJobById", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getBatchJobById", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during getBatchJobById", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all BatchJobs", description = "Retrieve all BatchJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BatchJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllBatchJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            List<BatchJobResponse> list = mapper.convertValue(array, new TypeReference<List<BatchJobResponse>>() {});
            return ResponseEntity.ok(list);

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getAllBatchJobs", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getAllBatchJobs", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during getAllBatchJobs", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update BatchJob", description = "Update a BatchJob entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateBatchJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateBatchJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "BatchJob update payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateBatchJobRequest.class))
            ) @RequestBody UpdateBatchJobRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);

            BatchJob data = new BatchJob();
            data.setJobName(request.getJobName());
            data.setScheduleCron(request.getScheduleCron());
            data.setRunMonth(request.getRunMonth());
            data.setCreatedAt(request.getCreatedAt());
            data.setStartedAt(request.getStartedAt());
            data.setFinishedAt(request.getFinishedAt());
            data.setStatus(request.getStatus());
            data.setSummary(request.getSummary());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    id,
                    data
            );

            UUID updatedId = updatedIdFuture.get();
            UpdateBatchJobResponse resp = new UpdateBatchJobResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when updating BatchJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updateBatchJob", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateBatchJob", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during updateBatchJob", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete BatchJob", description = "Delete a BatchJob entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteBatchJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteBatchJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            DeleteBatchJobResponse resp = new DeleteBatchJobResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when deleting BatchJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deleteBatchJob", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteBatchJob", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during deleteBatchJob", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /* Static DTO classes for request/response payloads */

    @Data
    @Schema(name = "CreateBatchJobRequest", description = "Request payload to create a BatchJob")
    public static class CreateBatchJobRequest {
        @Schema(description = "Human name for the scheduled batch run", example = "MonthlyUserBatch")
        private String jobName;

        @Schema(description = "Cron expression for scheduling", example = "0 0 1 * *")
        private String scheduleCron;

        @Schema(description = "Run month in YYYY-MM format", example = "2025-09")
        private String runMonth;
    }

    @Data
    @Schema(name = "CreateBatchJobResponse", description = "Response containing technicalId for created BatchJob")
    public static class CreateBatchJobResponse {
        @Schema(description = "Technical ID of the created entity", example = "job-0001-uuid")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchJobResponse", description = "BatchJob entity representation")
    public static class BatchJobResponse {
        @Schema(description = "Human name for the scheduled batch run")
        private String jobName;

        @Schema(description = "Cron expression for scheduling")
        private String scheduleCron;

        @Schema(description = "Run month in YYYY-MM format")
        private String runMonth;

        @Schema(description = "Timestamp when job entity persisted")
        private String createdAt;

        @Schema(description = "Timestamp when processing started")
        private String startedAt;

        @Schema(description = "Timestamp when processing finished")
        private String finishedAt;

        @Schema(description = "Status of the job (PENDING/VALIDATING/RUNNING/COMPLETED/FAILED)")
        private String status;

        @Schema(description = "Short run summary / error summary")
        private String summary;
    }

    @Data
    @Schema(name = "UpdateBatchJobRequest", description = "Payload to update a BatchJob")
    public static class UpdateBatchJobRequest {
        @Schema(description = "Human name for the scheduled batch run")
        private String jobName;

        @Schema(description = "Cron expression for scheduling")
        private String scheduleCron;

        @Schema(description = "Run month in YYYY-MM format")
        private String runMonth;

        @Schema(description = "Timestamp when job entity persisted")
        private String createdAt;

        @Schema(description = "Timestamp when processing started")
        private String startedAt;

        @Schema(description = "Timestamp when processing finished")
        private String finishedAt;

        @Schema(description = "Status of the job (PENDING/VALIDATING/RUNNING/COMPLETED/FAILED)")
        private String status;

        @Schema(description = "Short run summary / error summary")
        private String summary;
    }

    @Data
    @Schema(name = "UpdateBatchJobResponse", description = "Response containing technicalId for updated BatchJob")
    public static class UpdateBatchJobResponse {
        @Schema(description = "Technical ID of the updated entity", example = "job-0001-uuid")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteBatchJobResponse", description = "Response containing technicalId for deleted BatchJob")
    public static class DeleteBatchJobResponse {
        @Schema(description = "Technical ID of the deleted entity", example = "job-0001-uuid")
        private String technicalId;
    }
}