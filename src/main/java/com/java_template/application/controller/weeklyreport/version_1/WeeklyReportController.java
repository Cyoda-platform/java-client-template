package com.java_template.application.controller.weeklyreport.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "WeeklyReport", description = "WeeklyReport orchestration endpoints (version 1)")
public class WeeklyReportController {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyReportController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeeklyReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create on-demand WeeklyReport", description = "Create an on-demand WeeklyReport orchestration entity. Returns only technicalId.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createWeeklyReport(
            @RequestBody(description = "WeeklyReport creation request", required = true,
                content = @Content(schema = @Schema(implementation = WeeklyReportCreateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody WeeklyReportCreateRequest request) {
        try {
            // Basic request validation
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getReportId() == null || request.getReportId().isBlank()) {
                throw new IllegalArgumentException("reportId is required");
            }
            if (request.getWeekStart() == null || request.getWeekStart().isBlank()) {
                throw new IllegalArgumentException("weekStart is required");
            }

            // Create entity instance and set minimal required fields.
            WeeklyReport entity = new WeeklyReport();
            entity.setReportId(request.getReportId());
            entity.setWeekStart(request.getWeekStart());
            // Set generatedAt to now and initial status CREATED to satisfy entity validation.
            entity.setGeneratedAt(Instant.now().toString());
            entity.setStatus("CREATED");
            // summary and attachmentUrl deliberately left null

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    WeeklyReport.ENTITY_NAME,
                    WeeklyReport.ENTITY_VERSION,
                    entity
            );

            java.util.UUID createdId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(createdId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create WeeklyReport: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Failed to create WeeklyReport", e);
            return ResponseEntity.status(500).body(errorBody("Internal server error"));
        }
    }

    @Operation(summary = "Get WeeklyReport by technicalId", description = "Retrieve a WeeklyReport by its technicalId")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WeeklyReportResponse.class)))
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getWeeklyReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null) {
                return ResponseEntity.status(404).body(errorBody("WeeklyReport not found"));
            }

            JsonNode dataNode = dataPayload.getData() != null ? dataPayload.getData() : null;
            WeeklyReportResponse response;
            if (dataNode != null && !dataNode.isNull()) {
                response = objectMapper.treeToValue(dataNode, WeeklyReportResponse.class);
            } else {
                // As a fallback, build minimal response with technicalId
                response = new WeeklyReportResponse();
            }

            // attempt to extract technicalId from meta if present
            JsonNode metaNode = dataPayload.getMeta();
            if (metaNode != null && metaNode.has("entityId")) {
                response.setTechnicalId(metaNode.get("entityId").asText());
            } else {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get WeeklyReport: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Failed to retrieve WeeklyReport", e);
            return ResponseEntity.status(500).body(errorBody("Internal server error"));
        }
    }

    @Operation(summary = "List WeeklyReports", description = "Retrieve all WeeklyReport orchestration entities (no paging).")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = WeeklyReportResponse.class))))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = {})
    public ResponseEntity<?> listWeeklyReports() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    WeeklyReport.ENTITY_NAME,
                    WeeklyReport.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<WeeklyReportResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    WeeklyReportResponse resp = null;
                    if (data != null && !data.isNull()) {
                        resp = objectMapper.treeToValue(data, WeeklyReportResponse.class);
                    } else {
                        resp = new WeeklyReportResponse();
                    }
                    JsonNode meta = payload.getMeta();
                    if (meta != null && meta.has("entityId")) {
                        resp.setTechnicalId(meta.get("entityId").asText());
                    }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to list WeeklyReports: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to list WeeklyReports", e);
            return ResponseEntity.status(500).body(errorBody("Internal server error"));
        }
    }

    @Operation(summary = "Update WeeklyReport by technicalId", description = "Update a WeeklyReport orchestration entity. Client should provide full entity payload.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateWeeklyReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @RequestBody(description = "WeeklyReport update request", required = true,
                content = @Content(schema = @Schema(implementation = WeeklyReportUpdateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody WeeklyReportUpdateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            WeeklyReport entity = new WeeklyReport();
            entity.setReportId(request.getReportId());
            entity.setWeekStart(request.getWeekStart());
            entity.setGeneratedAt(request.getGeneratedAt());
            entity.setStatus(request.getStatus());
            entity.setSummary(request.getSummary());
            entity.setAttachmentUrl(request.getAttachmentUrl());

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updatedFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update WeeklyReport: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Failed to update WeeklyReport", e);
            return ResponseEntity.status(500).body(errorBody("Internal server error"));
        }
    }

    @Operation(summary = "Delete WeeklyReport by technicalId", description = "Delete a WeeklyReport orchestration entity by technicalId")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteWeeklyReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete WeeklyReport: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Failed to delete WeeklyReport", e);
            return ResponseEntity.status(500).body(errorBody("Internal server error"));
        }
    }

    // Helper to handle ExecutionException unwrapping as per requirements
    private ResponseEntity<?> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(404).body(errorBody(cause.getMessage()));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(errorBody(cause.getMessage()));
        } else {
            logger.error("Execution exception", ee);
            return ResponseEntity.status(500).body(errorBody("Internal server error"));
        }
    }

    private ErrorResponse errorBody(String message) {
        ErrorResponse err = new ErrorResponse();
        err.setMessage(message);
        return err;
    }

    // --- DTOs ---

    @Data
    @Schema(name = "WeeklyReportCreateRequest", description = "Request to create an on-demand WeeklyReport")
    public static class WeeklyReportCreateRequest {
        @Schema(description = "Business report id, e.g., weekly-summary-2025-W34", required = true, example = "weekly-summary-2025-W34")
        private String reportId;

        @Schema(description = "Week start date (ISO), e.g., 2025-08-18", required = true, example = "2025-08-18")
        private String weekStart;

        @Schema(description = "Template name to apply", required = false, example = "sales_summary_v1")
        private String template;

        @Schema(description = "Notification email", required = false, example = "victoria.sagdieva@cyoda.com")
        private String notifyEmail;
    }

    @Data
    @Schema(name = "WeeklyReportUpdateRequest", description = "Request to update WeeklyReport (full entity representation recommended)")
    public static class WeeklyReportUpdateRequest {
        @Schema(description = "Business report id", example = "weekly-summary-2025-W34")
        private String reportId;

        @Schema(description = "Week start date (ISO)", example = "2025-08-18")
        private String weekStart;

        @Schema(description = "When the report was generated (ISO datetime)", example = "2025-08-25T09:15:00Z")
        private String generatedAt;

        @Schema(description = "Report status", example = "DISPATCHED")
        private String status;

        @Schema(description = "Attachment URL (PDF)", example = "https://filestore/reports/report_9a0b1c2d.pdf")
        private String attachmentUrl;

        @Schema(description = "Short summary text", example = "Top seller: Dog Food X; 3 SKUs need restocking")
        private String summary;
    }

    @Data
    @Schema(name = "WeeklyReportResponse", description = "WeeklyReport response payload")
    public static class WeeklyReportResponse {
        @Schema(description = "Technical id of the entity", example = "report_9a0b1c2d")
        private String technicalId;

        @Schema(description = "Business report id", example = "weekly-summary-2025-W34")
        private String reportId;

        @Schema(description = "Week start date (ISO)", example = "2025-08-18")
        private String weekStart;

        @Schema(description = "When the report was generated (ISO datetime)", example = "2025-08-25T09:15:00Z")
        private String generatedAt;

        @Schema(description = "Report status", example = "DISPATCHED")
        private String status;

        @Schema(description = "Attachment URL (PDF)", example = "https://filestore/reports/report_9a0b1c2d.pdf")
        private String attachmentUrl;

        @Schema(description = "Short summary text", example = "Top seller: Dog Food X; 3 SKUs need restocking")
        private String summary;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id", example = "report_9a0b1c2d")
        private String technicalId;
    }

    @Data
    @Schema(name = "ErrorResponse", description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String message;
    }
}