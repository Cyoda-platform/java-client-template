package com.java_template.application.controller.reportjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.service.EntityService;
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
import java.util.Map;
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