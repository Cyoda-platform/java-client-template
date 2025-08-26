package com.java_template.application.controller.analysisjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.analysisjob.version_1.AnalysisJob;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "AnalysisJob", description = "API for AnalysisJob entity (version 1)")
public class AnalysisJobController {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AnalysisJob", description = "Create a new AnalysisJob. Controller only proxies to EntityService.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Analysis job create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateAnalysisJobRequest.class)))
            @RequestBody CreateAnalysisJobRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            AnalysisJob job = new AnalysisJob();
            // basic required metadata creation (no business logic beyond necessary fields)
            job.setId(UUID.randomUUID().toString());
            job.setCreatedAt(Instant.now().toString());
            job.setDataFeedId(request.getDataFeedId());
            job.setRequestedBy(request.getRequestedBy());
            job.setRunMode(request.getRunMode());
            job.setScheduleSpec(request.getScheduleSpec());
            // initial status for created entity (kept minimal)
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AnalysisJob.ENTITY_NAME,
                    String.valueOf(AnalysisJob.ENTITY_VERSION),
                    job
            );

            UUID createdId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(createdId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid create request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException creating AnalysisJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating AnalysisJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error creating AnalysisJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get AnalysisJob by technicalId", description = "Retrieve an AnalysisJob by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AnalysisJobResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AnalysisJob.ENTITY_NAME,
                    String.valueOf(AnalysisJob.ENTITY_VERSION),
                    tid
            );

            ObjectNode node = itemFuture.get();
            AnalysisJobResponse resp = objectMapper.convertValue(node, AnalysisJobResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid get request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException retrieving AnalysisJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving AnalysisJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving AnalysisJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all AnalysisJobs", description = "Retrieve all AnalysisJobs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AnalysisJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    AnalysisJob.ENTITY_NAME,
                    String.valueOf(AnalysisJob.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            List<AnalysisJobResponse> list = objectMapper.convertValue(arr, new TypeReference<List<AnalysisJobResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException retrieving all AnalysisJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all AnalysisJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving all AnalysisJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Filter AnalysisJobs", description = "Filter AnalysisJobs using SearchConditionRequest. Controller proxies to EntityService.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AnalysisJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> filterJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");

            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    AnalysisJob.ENTITY_NAME,
                    String.valueOf(AnalysisJob.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredFuture.get();
            List<AnalysisJobResponse> list = objectMapper.convertValue(arr, new TypeReference<List<AnalysisJobResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid filter request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException filtering AnalysisJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering AnalysisJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error filtering AnalysisJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update AnalysisJob", description = "Update an existing AnalysisJob. Controller only proxies to EntityService.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateAnalysisJobRequest.class)))
            @RequestBody UpdateAnalysisJobRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            UUID tid = UUID.fromString(technicalId);

            AnalysisJob job = new AnalysisJob();
            job.setId(technicalId); // keep as string
            // set only provided fields (controller does not implement business logic)
            job.setDataFeedId(request.getDataFeedId());
            job.setRequestedBy(request.getRequestedBy());
            job.setRunMode(request.getRunMode());
            job.setScheduleSpec(request.getScheduleSpec());
            job.setReportRef(request.getReportRef());
            job.setStatus(request.getStatus());
            job.setStartedAt(request.getStartedAt());
            job.setCompletedAt(request.getCompletedAt());
            job.setCreatedAt(request.getCreatedAt());

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    AnalysisJob.ENTITY_NAME,
                    String.valueOf(AnalysisJob.ENTITY_VERSION),
                    tid,
                    job
            );

            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException updating AnalysisJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating AnalysisJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error updating AnalysisJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete AnalysisJob", description = "Delete an AnalysisJob by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    AnalysisJob.ENTITY_NAME,
                    String.valueOf(AnalysisJob.ENTITY_VERSION),
                    tid
            );

            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException deleting AnalysisJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting AnalysisJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error deleting AnalysisJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateAnalysisJobRequest", description = "Payload to create an AnalysisJob")
    public static class CreateAnalysisJobRequest {
        @Schema(description = "Reference id of DataFeed", example = "df_12345", required = true)
        private String dataFeedId;
        @Schema(description = "User or system requesting the job", example = "analyst@example.com")
        private String requestedBy;
        @Schema(description = "Run mode e.g., AD_HOC or SCHEDULED", example = "AD_HOC", required = true)
        private String runMode;
        @Schema(description = "Schedule specification (optional)", example = "0 0 * * *")
        private String scheduleSpec;
    }

    @Data
    @Schema(name = "UpdateAnalysisJobRequest", description = "Payload to update an AnalysisJob (partial fields allowed)")
    public static class UpdateAnalysisJobRequest {
        @Schema(description = "Reference id of DataFeed", example = "df_12345")
        private String dataFeedId;
        @Schema(description = "User or system requesting the job", example = "analyst@example.com")
        private String requestedBy;
        @Schema(description = "Run mode e.g., AD_HOC or SCHEDULED", example = "AD_HOC")
        private String runMode;
        @Schema(description = "Schedule specification", example = "0 0 * * *")
        private String scheduleSpec;
        @Schema(description = "Reference to generated report", example = "report_001")
        private String reportRef;
        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;
        @Schema(description = "ISO timestamp when started", example = "2025-08-01T12:01:00Z")
        private String startedAt;
        @Schema(description = "ISO timestamp when completed", example = "2025-08-01T12:03:30Z")
        private String completedAt;
        @Schema(description = "Created at timestamp (optional when updating)", example = "2025-08-01T11:59:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the entity", example = "job_67890")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "AnalysisJobResponse", description = "Representation of AnalysisJob returned by the API")
    public static class AnalysisJobResponse {
        @Schema(description = "Technical id of the job", example = "job_67890")
        private String id;
        @Schema(description = "DataFeed reference id", example = "df_12345")
        private String dataFeedId;
        @Schema(description = "User or system who requested the job", example = "analyst@example.com")
        private String requestedBy;
        @Schema(description = "Run mode", example = "AD_HOC")
        private String runMode;
        @Schema(description = "Schedule specification", example = "0 0 * * *")
        private String scheduleSpec;
        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;
        @Schema(description = "Reference to generated report", example = "report_001")
        private String reportRef;
        @Schema(description = "ISO timestamp when created", example = "2025-08-01T11:59:00Z")
        private String createdAt;
        @Schema(description = "ISO timestamp when started", example = "2025-08-01T12:01:00Z")
        private String startedAt;
        @Schema(description = "ISO timestamp when completed", example = "2025-08-01T12:03:30Z")
        private String completedAt;
    }
}