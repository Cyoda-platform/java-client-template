package com.java_template.application.controller.reportjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/report-jobs")
@Tag(name = "ReportJob", description = "Operations for ReportJob entity (proxy to EntityService)")
public class ReportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ReportJobController.class);

    private final EntityService entityService;

    public ReportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ReportJob", description = "Create a ReportJob which will trigger event-driven processing. Returns the technicalId of the created job.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createReportJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ReportJob creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateReportJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateReportJobRequest request
    ) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest().body("Request body is required");
            }
            ReportJob data = new ReportJob();
            data.setRequestedBy(request.getRequestedBy());
            data.setTitle(request.getTitle());
            data.setFilters(request.getFilters());
            data.setVisualization(request.getVisualization());
            data.setExportFormats(request.getExportFormats());
            data.setNotify(request.getNotify());
            // No business logic here - just proxy to entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            CreateReportJobResponse resp = new CreateReportJobResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request to create ReportJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.error("Entity not found during create ReportJob", cause);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument during create ReportJob", cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating ReportJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple ReportJobs", description = "Create multiple ReportJobs in bulk. Returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createReportJobsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk create payload", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateReportJobRequest.class))))
            @org.springframework.web.bind.annotation.RequestBody List<CreateReportJobRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                return ResponseEntity.badRequest().body("Request body with at least one item is required");
            }
            List<ReportJob> entities = new ArrayList<>();
            for (CreateReportJobRequest r : requests) {
                ReportJob data = new ReportJob();
                data.setRequestedBy(r.getRequestedBy());
                data.setTitle(r.getTitle());
                data.setFilters(r.getFilters());
                data.setVisualization(r.getVisualization());
                data.setExportFormats(r.getExportFormats());
                data.setNotify(r.getNotify());
                entities.add(data);
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            BulkCreateResponse resp = new BulkCreateResponse();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).collect(Collectors.toList()));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid bulk create request", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.error("Entity not found during bulk create", cause);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument during bulk create", cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during bulk create", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during bulk create", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during bulk create", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ReportJob by technicalId", description = "Retrieve a ReportJob by its technical id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();

            ReportJobResponse resp = new ReportJobResponse();
            resp.setTechnicalId(technicalId);
            if (node != null) {
                if (node.has("requestedBy") && !node.get("requestedBy").isNull()) {
                    resp.setRequestedBy(node.get("requestedBy").asText());
                }
                if (node.has("title") && !node.get("title").isNull()) {
                    resp.setTitle(node.get("title").asText());
                }
                if (node.has("createdAt") && !node.get("createdAt").isNull()) {
                    resp.setCreatedAt(node.get("createdAt").asText());
                }
                if (node.has("status") && !node.get("status").isNull()) {
                    resp.setStatus(node.get("status").asText());
                }
                // reportReference may be present in stored JSON even if not defined in entity class
                if (node.has("reportReference") && !node.get("reportReference").isNull()) {
                    resp.setReportReference(node.get("reportReference").asText());
                }
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getReportJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.error("ReportJob not found", cause);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument during getReportJob", cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching ReportJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while fetching ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all ReportJobs", description = "Retrieve all ReportJob entities (raw JSON array returned).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllReportJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument during getAllReportJobs", cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching all ReportJobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching all ReportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while fetching all ReportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search ReportJobs by condition", description = "Search ReportJobs using a SearchConditionRequest. Returns matching entities as raw JSON array.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchReportJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody SearchConditionRequest condition
    ) {
        try {
            if (condition == null) {
                return ResponseEntity.badRequest().body("Search condition is required");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode result = filteredItemsFuture.get();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid search condition", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument during searchReportJobs", cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching ReportJobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching ReportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while searching ReportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update ReportJob", description = "Update a ReportJob by technical id. Returns the technicalId of the updated entity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateReportJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateReportJobRequest request
    ) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest().body("Request body is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            ReportJob data = new ReportJob();
            data.setRequestedBy(request.getRequestedBy());
            data.setTitle(request.getTitle());
            data.setFilters(request.getFilters());
            data.setVisualization(request.getVisualization());
            data.setExportFormats(request.getExportFormats());
            data.setNotify(request.getNotify());
            data.setStatus(request.getStatus());
            data.setCreatedAt(request.getCreatedAt());
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    uuid,
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            UpdateReportJobResponse resp = new UpdateReportJobResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request to update ReportJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.error("ReportJob not found during update", cause);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument during updateReportJob", cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating ReportJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while updating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete ReportJob", description = "Delete a ReportJob by technical id. Returns the technicalId of the deleted entity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    uuid
            );
            UUID deletedId = deletedIdFuture.get();
            DeleteReportJobResponse resp = new DeleteReportJobResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for deleteReportJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.error("ReportJob not found during delete", cause);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument during deleteReportJob", cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting ReportJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while deleting ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs

    @Data
    @Schema(name = "CreateReportJobRequest", description = "Payload to create a ReportJob")
    public static class CreateReportJobRequest {
        @Schema(description = "User who requested the report", example = "user@example.com", required = true)
        private String requestedBy;

        @Schema(description = "Report title", example = "Monthly Inventory Value", required = true)
        private String title;

        @Schema(description = "Filters to apply when fetching inventory", required = false)
        private Map<String, String> filters;

        @Schema(description = "Visualization preference (e.g., table, chart)", example = "table_bar", required = false)
        private String visualization;

        @Schema(description = "Export formats (e.g., CSV, PDF)", required = true)
        private List<String> exportFormats;

        @Schema(description = "Notification target (email)", example = "user@example.com", required = false)
        private String notify;
    }

    @Data
    @Schema(name = "CreateReportJobResponse", description = "Response containing the technical id of created ReportJob")
    public static class CreateReportJobResponse {
        @Schema(description = "Technical id of the created ReportJob", example = "rj-0001-uuid")
        private String technicalId;
    }

    @Data
    @Schema(name = "BulkCreateResponse", description = "Response containing the technical ids of created ReportJobs")
    public static class BulkCreateResponse {
        @Schema(description = "List of technical ids of created ReportJobs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "UpdateReportJobRequest", description = "Payload to update a ReportJob (partial or full)")
    public static class UpdateReportJobRequest {
        @Schema(description = "User who requested the report", example = "user@example.com")
        private String requestedBy;

        @Schema(description = "Report title", example = "Monthly Inventory Value")
        private String title;

        @Schema(description = "Filters to apply when fetching inventory")
        private Map<String, String> filters;

        @Schema(description = "Visualization preference (e.g., table, chart)", example = "table_bar")
        private String visualization;

        @Schema(description = "Export formats (e.g., CSV, PDF)")
        private List<String> exportFormats;

        @Schema(description = "Notification target (email)", example = "user@example.com")
        private String notify;

        @Schema(description = "Current status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "ISO timestamp when the job was created", example = "2025-08-27T12:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "UpdateReportJobResponse", description = "Response containing the technical id of updated ReportJob")
    public static class UpdateReportJobResponse {
        @Schema(description = "Technical id of the updated ReportJob", example = "rj-0001-uuid")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteReportJobResponse", description = "Response containing the technical id of deleted ReportJob")
    public static class DeleteReportJobResponse {
        @Schema(description = "Technical id of the deleted ReportJob", example = "rj-0001-uuid")
        private String technicalId;
    }

    @Data
    @Schema(name = "ReportJobResponse", description = "ReportJob retrieval response")
    public static class ReportJobResponse {
        @Schema(description = "Technical id of the ReportJob", example = "rj-0001-uuid")
        private String technicalId;

        @Schema(description = "User who requested the report", example = "user@example.com")
        private String requestedBy;

        @Schema(description = "Report title", example = "Monthly Inventory Value")
        private String title;

        @Schema(description = "ISO timestamp when the job was created", example = "2025-08-27T12:00:00Z")
        private String createdAt;

        @Schema(description = "Current status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "Reference to generated report if available", example = "rep-0001-uuid")
        private String reportReference;
    }
}