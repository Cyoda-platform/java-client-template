package com.java_template.application.controller.reportjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/jobs/report")
@Tag(name = "ReportJob", description = "Operations for ReportJob orchestration entity")
public class ReportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ReportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ReportJob", description = "Create a ReportJob orchestration (triggers workflow). Returns the technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createReportJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReportJobCreateRequest.class)))
            @RequestBody ReportJobCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getDataSourceUrl() == null || request.getDataSourceUrl().isBlank()) {
                throw new IllegalArgumentException("data_source_url is required");
            }
            if (request.getTriggerType() == null || request.getTriggerType().isBlank()) {
                throw new IllegalArgumentException("trigger_type is required");
            }

            ReportJob entity = new ReportJob();
            // Minimal population — controller proxies persistence only. Business logic lives in workflows.
            String technicalId = UUID.randomUUID().toString();
            entity.setJobId(technicalId);
            entity.setDataSourceUrl(request.getDataSourceUrl());
            entity.setRequestedMetrics(request.getRequestedMetrics());
            entity.setNotifyFilters(request.getNotifyFilters());
            entity.setTriggerType(request.getTriggerType());
            // set initial status and timestamps minimally to satisfy entity validation if needed
            entity.setStatus("PENDING");
            entity.setGeneratedAt(Instant.now().toString());
            // reportLocation left null

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ReportJob.ENTITY_NAME,
                    ReportJob.ENTITY_VERSION,
                    entity
            );
            UUID createdId = idFuture.get();
            // Return the technical id (string) as specified in requirements
            return ResponseEntity.ok(createdId.toString());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createReportJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createReportJob", e);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createReportJob", e);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error during createReportJob", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get ReportJob", description = "Retrieve ReportJob orchestration by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportJobResponse.class))),
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
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(id);
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("ReportJob not found");
            }
            JsonNode dataNode = dataPayload.getData();
            ReportJobResponse response = objectMapper.treeToValue(dataNode, ReportJobResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getReportJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getReportJob", e);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getReportJob", e);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error during getReportJob", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Data
    @Schema(name = "ReportJobCreateRequest", description = "Payload to create a ReportJob orchestration")
    public static class ReportJobCreateRequest {
        @Schema(name = "data_source_url", description = "URL to process", required = true, example = "https://raw.githubusercontent.com/.../london_houses.csv")
        private String dataSourceUrl;

        @Schema(name = "trigger_type", description = "manual/scheduled/on_change", required = true, example = "manual")
        private String triggerType;

        @Schema(name = "requested_metrics", description = "specification of analytics to run", example = "avg_price,median_price")
        private String requestedMetrics;

        @Schema(name = "notify_filters", description = "subscriber filter rules", example = "frequency=weekly;area=All")
        private String notifyFilters;

        @Schema(name = "schedule", description = "schedule metadata (optional)", example = "null")
        private String schedule;
    }

    @Data
    @Schema(name = "ReportJobResponse", description = "ReportJob representation returned to clients")
    public static class ReportJobResponse {
        @Schema(name = "job_id", description = "internal job id")
        private String jobId;

        @Schema(name = "data_source_url", description = "URL to process")
        private String dataSourceUrl;

        @Schema(name = "trigger_type", description = "trigger type")
        private String triggerType;

        @Schema(name = "requested_metrics", description = "requested metrics")
        private String requestedMetrics;

        @Schema(name = "status", description = "current job status")
        private String status;

        @Schema(name = "report_location", description = "where generated report is stored")
        private String reportLocation;

        @Schema(name = "generated_at", description = "timestamp when report was generated")
        private String generatedAt;

        @Schema(name = "notify_filters", description = "subscriber filter rules")
        private String notifyFilters;
    }
}