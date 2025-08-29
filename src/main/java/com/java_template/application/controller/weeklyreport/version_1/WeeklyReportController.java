package com.java_template.application.controller.weeklyreport.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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