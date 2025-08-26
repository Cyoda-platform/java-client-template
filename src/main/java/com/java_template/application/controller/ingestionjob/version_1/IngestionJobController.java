package com.java_template.application.controller.ingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/ingestion-job")
@Tag(name = "IngestionJob", description = "Controller for IngestionJob entity (version 1). Proxy to EntityService; workflows handle business logic.")
public class IngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobController.class);

    private final EntityService entityService;

    public IngestionJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Ingestion Job", description = "Creates an IngestionJob entity. This endpoint is a proxy to EntityService and triggers the ingestion workflow. Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/jobs/ingest")
    public ResponseEntity<?> createIngestionJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Ingestion job create request", required = true,
                    content = @Content(schema = @Schema(implementation = IngestionJobCreateRequest.class)))
            @Valid @RequestBody IngestionJobCreateRequest request) {
        try {
            // Build entity instance (controller does not implement business logic)
            IngestionJob job = new IngestionJob();
            job.setSchedule(request.getSchedule());
            job.setSourceEndpoint(request.getSourceEndpoint());
            job.setInitiatedBy(request.getInitiatedBy());

            UUID technicalId = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    job
            ).get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createIngestionJob: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createIngestionJob: {}", cause.getMessage(), cause);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createIngestionJob: {}", cause.getMessage(), cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createIngestionJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createIngestionJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during createIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Ingestion Job by technicalId", description = "Retrieves an IngestionJob entity by its technicalId. Proxy to EntityService.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IngestionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/jobs/{technicalId}")
    public ResponseEntity<?> getIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            ObjectNode item = entityService.getItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getIngestionJob: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("IngestionJob not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getIngestionJob: {}", cause.getMessage(), cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getIngestionJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getIngestionJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during getIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "IngestionJobCreateRequest", description = "Request payload to create an IngestionJob")
    public static class IngestionJobCreateRequest {
        @NotBlank
        @Schema(description = "Cron or weekly schedule", example = "0 0 0 ? * MON", required = true)
        private String schedule;

        @NotBlank
        @Schema(description = "Source endpoint to fetch data from", example = "https://fakerestapi.azurewebsites.net/api/covers", required = true)
        private String sourceEndpoint;

        @NotBlank
        @Schema(description = "Who initiated the job (system/user)", example = "system", required = true)
        private String initiatedBy;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "IngestionJobResponse", description = "Representation of an IngestionJob returned by GET")
    public static class IngestionJobResponse {
        @Schema(description = "Business job id", example = "job-001")
        private String jobId;

        @Schema(description = "Cron or weekly schedule", example = "0 0 0 ? * MON")
        private String schedule;

        @Schema(description = "Source endpoint", example = "https://fakerestapi.azurewebsites.net/api/covers")
        private String sourceEndpoint;

        @Schema(description = "Current status", example = "COMPLETED")
        private String status;

        @Schema(description = "Number of processed records", example = "42")
        private Integer processedCount;

        @Schema(description = "Start timestamp", example = "2025-08-01T00:00:00Z")
        private String startedAt;

        @Schema(description = "Finish timestamp", example = "2025-08-01T00:05:00Z")
        private String finishedAt;

        @Schema(description = "Summary of errors if any", example = "")
        private String errorSummary;
    }
}