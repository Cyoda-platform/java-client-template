package com.java_template.application.controller.reportjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/report-jobs")
@Tag(name = "ReportJob", description = "Operations for ReportJob entity (proxy to EntityService)")
public class ReportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ReportJobController.class);

    private final EntityService entityService;

    public ReportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ReportJob", description = "Create a new ReportJob orchestration entity. Returns only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = CreateReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createReportJob(@Valid @RequestBody CreateReportJobRequest request) {
        try {
            ReportJob data = new ReportJob();
            data.setName(request.getName());
            data.setPeriodStart(request.getPeriodStart());
            data.setPeriodEnd(request.getPeriodEnd());
            data.setTemplateId(request.getTemplateId());
            data.setOutputFormats(request.getOutputFormats());
            data.setRecipients(request.getRecipients());
            // initial state set to PENDING per workflow initial state
            data.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            return ResponseEntity.status(HttpStatus.CREATED).body(new CreateReportJobResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for createReportJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException during createReportJob", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during createReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple ReportJobs", description = "Bulk create ReportJob entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = BulkCreateReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createReportJobsBulk(@Valid @RequestBody List<CreateReportJobRequest> requests) {
        try {
            List<ReportJob> entities = new ArrayList<>();
            for (CreateReportJobRequest req : requests) {
                ReportJob data = new ReportJob();
                data.setName(req.getName());
                data.setPeriodStart(req.getPeriodStart());
                data.setPeriodEnd(req.getPeriodEnd());
                data.setTemplateId(req.getTemplateId());
                data.setOutputFormats(req.getOutputFormats());
                data.setRecipients(req.getRecipients());
                data.setStatus("PENDING");
                entities.add(data);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = new ArrayList<>();
            for (UUID u : ids) technicalIds.add(u.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(new BulkCreateReportJobResponse(technicalIds));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for createReportJobsBulk", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException during createReportJobsBulk", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during createReportJobsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ReportJob by technicalId", description = "Retrieve a ReportJob by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getReportJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ReportJob not found");
            }
            ReportJobResponse resp = mapObjectNodeToResponse(node);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId for getReportJobById", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException during getReportJobById", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during getReportJobById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all ReportJobs", description = "Retrieve all ReportJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listReportJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<ReportJobResponse> results = new ArrayList<>();
            if (array != null) {
                array.forEach(node -> {
                    if (node.isObject()) results.add(mapObjectNodeToResponse((ObjectNode) node));
                });
            }
            return ResponseEntity.ok(results);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException during listReportJobs", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during listReportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search ReportJobs by condition", description = "Filter ReportJobs using a SearchConditionRequest.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchReportJobs(@RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            List<ReportJobResponse> results = new ArrayList<>();
            if (array != null) {
                array.forEach(node -> {
                    if (node.isObject()) results.add(mapObjectNodeToResponse((ObjectNode) node));
                });
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid search condition for searchReportJobs", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException during searchReportJobs", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during searchReportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update ReportJob", description = "Update an existing ReportJob entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UpdateReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
            @Valid @RequestBody UpdateReportJobRequest request) {
        try {
            ReportJob data = new ReportJob();
            // Ensure technical id is set on entity if present
            data.setId(technicalId);
            data.setName(request.getName());
            data.setPeriodStart(request.getPeriodStart());
            data.setPeriodEnd(request.getPeriodEnd());
            data.setTemplateId(request.getTemplateId());
            data.setOutputFormats(request.getOutputFormats());
            data.setRecipients(request.getRecipients());
            // allow status update through request if provided
            if (request.getStatus() != null) data.setStatus(request.getStatus());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new UpdateReportJobResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for updateReportJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException during updateReportJob", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during updateReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete ReportJob", description = "Delete a ReportJob by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = DeleteReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new DeleteReportJobResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId for deleteReportJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException during deleteReportJob", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during deleteReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ReportJobResponse mapObjectNodeToResponse(ObjectNode node) {
        ReportJobResponse resp = new ReportJobResponse();
        // entity may use "id" field for technical id
        if (node.hasNonNull("id")) resp.setTechnicalId(node.get("id").asText());
        else if (node.hasNonNull("technicalId")) resp.setTechnicalId(node.get("technicalId").asText());
        else if (node.hasNonNull("uuid")) resp.setTechnicalId(node.get("uuid").asText());

        if (node.hasNonNull("name")) resp.setName(node.get("name").asText());
        if (node.hasNonNull("status")) resp.setStatus(node.get("status").asText());
        if (node.hasNonNull("generatedUrl")) resp.setGeneratedUrl(node.get("generatedUrl").asText());
        else if (node.hasNonNull("generated_url")) resp.setGeneratedUrl(node.get("generated_url").asText());
        if (node.hasNonNull("createdAt")) resp.setCreatedAt(node.get("createdAt").asText());
        else if (node.hasNonNull("created_at")) resp.setCreatedAt(node.get("created_at").asText());
        return resp;
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateReportJobRequest", description = "Request to create a ReportJob")
    public static class CreateReportJobRequest {
        @Schema(description = "Name of the report job", required = true, example = "Weekly Product Performance")
        private String name;

        @JsonProperty("period_start")
        @Schema(description = "Period start date", required = true, example = "2025-08-18")
        private String periodStart;

        @JsonProperty("period_end")
        @Schema(description = "Period end date", required = true, example = "2025-08-24")
        private String periodEnd;

        @JsonProperty("template_id")
        @Schema(description = "Template identifier", required = true, example = "weekly_v1")
        private String templateId;

        @JsonProperty("output_formats")
        @Schema(description = "Output formats (comma separated)", example = "PDF,CSV")
        private String outputFormats;

        @Schema(description = "Comma separated recipient emails", example = "victoria.sagdieva@cyoda.com")
        private String recipients;
    }

    @Data
    @Schema(name = "CreateReportJobResponse", description = "Response for create ReportJob")
    public static class CreateReportJobResponse {
        @Schema(description = "Technical ID of created entity", example = "report-67890")
        private String technicalId;

        public CreateReportJobResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BulkCreateReportJobResponse", description = "Response for bulk create ReportJobs")
    public static class BulkCreateReportJobResponse {
        @Schema(description = "List of technical IDs of created entities")
        private List<String> technicalIds;

        public BulkCreateReportJobResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "ReportJobResponse", description = "ReportJob read response")
    public static class ReportJobResponse {
        @JsonProperty("technicalId")
        @Schema(description = "Technical ID of the entity", example = "report-67890")
        private String technicalId;

        @Schema(description = "Name of the report job", example = "Weekly Product Performance")
        private String name;

        @Schema(description = "Status of the job", example = "SENDING")
        private String status;

        @JsonProperty("generated_url")
        @Schema(description = "Location of produced report", example = "s3://reports/report-67890.pdf")
        private String generatedUrl;

        @JsonProperty("created_at")
        @Schema(description = "Creation timestamp", example = "2025-08-25T09:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "UpdateReportJobRequest", description = "Request to update a ReportJob")
    public static class UpdateReportJobRequest {
        @Schema(description = "Name of the report job", example = "Weekly Product Performance")
        private String name;

        @JsonProperty("period_start")
        @Schema(description = "Period start date", example = "2025-08-18")
        private String periodStart;

        @JsonProperty("period_end")
        @Schema(description = "Period end date", example = "2025-08-24")
        private String periodEnd;

        @JsonProperty("template_id")
        @Schema(description = "Template identifier", example = "weekly_v1")
        private String templateId;

        @JsonProperty("output_formats")
        @Schema(description = "Output formats (comma separated)", example = "PDF,CSV")
        private String outputFormats;

        @Schema(description = "Comma separated recipient emails", example = "victoria.sagdieva@cyoda.com")
        private String recipients;

        @Schema(description = "Status of the report job", example = "PENDING")
        private String status;
    }

    @Data
    @Schema(name = "UpdateReportJobResponse", description = "Response for update ReportJob")
    public static class UpdateReportJobResponse {
        @Schema(description = "Technical ID of updated entity", example = "report-67890")
        private String technicalId;

        public UpdateReportJobResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeleteReportJobResponse", description = "Response for delete ReportJob")
    public static class DeleteReportJobResponse {
        @Schema(description = "Technical ID of deleted entity", example = "report-67890")
        private String technicalId;

        public DeleteReportJobResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}