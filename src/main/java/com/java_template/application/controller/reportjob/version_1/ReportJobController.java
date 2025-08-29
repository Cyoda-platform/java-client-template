package com.java_template.application.controller.reportjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

@RestController
@RequestMapping("/report-jobs")
@Tag(name = "ReportJob", description = "Operations for ReportJob entity (version 1)")
public class ReportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ReportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReportJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create ReportJob", description = "Create a new ReportJob (orchestration trigger). Returns the technicalId of the created job.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateReportJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report job creation payload", required = true,
        content = @Content(schema = @Schema(implementation = CreateReportJobRequest.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReportJob(@RequestBody CreateReportJobRequest request) {
        try {
            // Basic request format validation
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getRequestedBy() == null || request.getRequestedBy().isBlank()) {
                throw new IllegalArgumentException("requestedBy is required");
            }
            // Map to entity
            ReportJob entity = new ReportJob();
            entity.setName(request.getName());
            entity.setRequestedBy(request.getRequestedBy());
            entity.setIncludeCharts(request.getIncludeCharts());
            // map filters if provided
            if (request.getFilters() != null) {
                ReportJob.Filters f = new ReportJob.Filters();
                f.setDateFrom(request.getFilters().getDateFrom());
                f.setDateTo(request.getFilters().getDateTo());
                f.setDepositPaid(request.getFilters().getDepositPaid());
                f.setMinPrice(request.getFilters().getMinPrice());
                f.setMaxPrice(request.getFilters().getMaxPrice());
                entity.setFilters(f);
            }
            // Set minimal creation metadata (no business logic beyond defaults)
            entity.setRequestedAt(Instant.now().toString());
            entity.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                ReportJob.ENTITY_NAME,
                ReportJob.ENTITY_VERSION,
                entity
            );
            UUID entityId = idFuture.get();
            CreateReportJobResponse resp = new CreateReportJobResponse(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request to create ReportJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating ReportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ReportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ReportJob", description = "Retrieve a ReportJob by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetReportJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ReportJob not found");
            }
            JsonNode dataNode = dataPayload.getData();
            GetReportJobResponse resp = objectMapper.treeToValue(dataNode, GetReportJobResponse.class);

            // Attach technicalId from meta if present
            if (dataPayload.getMeta() != null && dataPayload.getMeta().has("entityId")) {
                resp.setTechnicalId(dataPayload.getMeta().get("entityId").asText());
            } else {
                resp.setTechnicalId(technicalId);
            }
            // Attach reportTechnicalId if present in meta
            if (dataPayload.getMeta() != null && dataPayload.getMeta().has("reportTechnicalId")) {
                resp.setReportTechnicalId(dataPayload.getMeta().get("reportTechnicalId").asText());
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request to get ReportJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving ReportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving ReportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Request/Response DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CreateReportJobRequest", description = "Payload to create a ReportJob")
    public static class CreateReportJobRequest {
        @Schema(description = "Human name for the report job", required = true, example = "June revenue report")
        private String name;

        @Schema(description = "User id or name who requested the job", required = true, example = "analyst_1")
        private String requestedBy;

        @Schema(description = "Filtering criteria for the report")
        private FiltersRequest filters;

        @Schema(description = "Include visualizations", example = "true")
        private Boolean includeCharts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "FiltersRequest", description = "Filters for the report job")
    public static class FiltersRequest {
        @Schema(description = "Start date (ISO)", example = "2025-06-01")
        private String dateFrom;

        @Schema(description = "End date (ISO)", example = "2025-06-30")
        private String dateTo;

        @Schema(description = "Minimum booking price", example = "0")
        private Integer minPrice;

        @Schema(description = "Maximum booking price", example = "1000")
        private Integer maxPrice;

        @Schema(description = "Deposit paid filter (true/false/null)", example = "null")
        private Boolean depositPaid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CreateReportJobResponse", description = "Response after creating a ReportJob")
    public static class CreateReportJobResponse {
        @Schema(description = "Technical ID of the created ReportJob", example = "rj-123456")
        private String technicalId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "GetReportJobResponse", description = "ReportJob retrieval response")
    public static class GetReportJobResponse {
        @Schema(description = "Technical ID of the job", example = "rj-123456")
        private String technicalId;

        @Schema(description = "Human name for the report job", example = "June revenue report")
        private String name;

        @Schema(description = "Current status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "User who requested the job", example = "analyst_1")
        private String requestedBy;

        @Schema(description = "When the job was requested (ISO timestamp)", example = "2025-07-01T10:00:00Z")
        private String requestedAt;

        @Schema(description = "When the job completed (ISO timestamp)", example = "2025-07-01T10:01:30Z")
        private String completedAt;

        @Schema(description = "Technical ID of the generated report, if available", example = "rep-98765")
        private String reportTechnicalId;
    }
}