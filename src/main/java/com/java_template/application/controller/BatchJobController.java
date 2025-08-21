package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/batchjobs")
@Tag(name = "BatchJob API", description = "Proxy controller for BatchJob entity")
public class BatchJobController {
    private static final Logger logger = LoggerFactory.getLogger(BatchJobController.class);

    private final EntityService entityService;

    public BatchJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create BatchJob", description = "Create a new BatchJob and start workflow asynchronously")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createBatchJob(@RequestBody CreateBatchJobRequest request) {
        try {
            // basic validation
            if (request.getJobName() == null || request.getJobName().isBlank()) {
                throw new IllegalArgumentException("jobName is required");
            }
            if (request.getTimezone() == null || request.getTimezone().isBlank()) {
                throw new IllegalArgumentException("timezone is required");
            }
            if (request.getAdminEmails() == null || request.getAdminEmails().isEmpty()) {
                throw new IllegalArgumentException("adminEmails is required");
            }

            BatchJob data = new BatchJob();
            data.setJobName(request.getJobName());
            data.setScheduledFor(request.getScheduledFor());
            data.setTimezone(request.getTimezone());
            data.setAdminEmails(request.getAdminEmails());
            data.setStatus("CREATED");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    data
            );

            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for creating BatchJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error creating BatchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating BatchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get BatchJob by technicalId", description = "Retrieve a BatchJob by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getBatchJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getBatchJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error getting BatchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error getting BatchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List BatchJobs", description = "List BatchJobs optionally filtered by status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = BatchJobResponse.class))))
    })
    @GetMapping
    public ResponseEntity<?> listBatchJobs(@RequestParam(required = false) String status) {
        try {
            if (status != null && !status.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.status", "EQUALS", status)
                );
                CompletableFuture<ArrayNode> filtered = entityService.getItemsByCondition(
                        BatchJob.ENTITY_NAME,
                        String.valueOf(BatchJob.ENTITY_VERSION),
                        condition,
                        true
                );
                ArrayNode arr = filtered.get();
                return ResponseEntity.ok(arr);
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        BatchJob.ENTITY_NAME,
                        String.valueOf(BatchJob.ENTITY_VERSION)
                );
                ArrayNode arr = itemsFuture.get();
                return ResponseEntity.ok(arr);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in listBatchJobs", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error listing BatchJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error listing BatchJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "CreateBatchJobRequest", description = "Request payload to create a BatchJob")
    public static class CreateBatchJobRequest {
        private String jobName;
        private String scheduledFor;
        private String timezone;
        private List<String> adminEmails;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the created technicalId")
    public static class TechnicalIdResponse {
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BatchJobResponse", description = "BatchJob response payload")
    public static class BatchJobResponse {
        private String technicalId;
        private String jobName;
        private String scheduledFor;
        private String timezone;
        private List<String> adminEmails;
        private String status;
        private String createdAt;
        private String startedAt;
        private String finishedAt;
        private Integer processedUserCount;
        private String errorMessage;
    }
}
