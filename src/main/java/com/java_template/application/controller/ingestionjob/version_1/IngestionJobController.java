package com.java_template.application.controller.ingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs/ingest")
@Tag(name = "IngestionJob", description = "Endpoints for managing ingestion jobs (version 1)")
public class IngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IngestionJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Ingestion Job", description = "Create an ingestion job. Returns only the technicalId of the created job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<String> createIngestionJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Ingestion job request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateIngestionJobRequest.class)))
            @RequestBody CreateIngestionJobRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is required");
            }
            if (request.getRequestedBy() == null || request.getRequestedBy().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("requestedBy is required");
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
            }

            IngestionJob entity = new IngestionJob();
            entity.setRequestedBy(request.getRequestedBy());
            entity.setSourceUrl(request.getSourceUrl());
            // Basic format normalization: ensure startedAt and status present
            if (request.getStartedAt() == null || request.getStartedAt().isBlank()) {
                entity.setStartedAt(Instant.now().toString());
            } else {
                entity.setStartedAt(request.getStartedAt());
            }
            // default initial status if not provided
            if (request.getStatus() == null || request.getStatus().isBlank()) {
                entity.setStatus("PENDING");
            } else {
                entity.setStatus(request.getStatus());
            }
            // Leave completedAt and summary as-is (null unless provided)
            if (request.getCompletedAt() != null && !request.getCompletedAt().isBlank()) {
                entity.setCompletedAt(request.getCompletedAt());
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    IngestionJob.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();
            return ResponseEntity.ok(entityId.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createIngestionJob: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createIngestionJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ingestion job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createIngestionJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Ingestion Job by technicalId", description = "Retrieve ingestion job details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IngestionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getIngestionJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(uuid);
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            }

            JsonNode dataNode = (JsonNode) dataPayload.getData();
            IngestionJob ingestionJob = objectMapper.treeToValue(dataNode, IngestionJob.class);

            String returnedTechnicalId = null;
            if (dataPayload.getMeta() != null && dataPayload.getMeta().get("entityId") != null) {
                JsonNode node = dataPayload.getMeta().get("entityId");
                if (node != null && !node.isNull()) {
                    returnedTechnicalId = node.asText();
                }
            }
            if (returnedTechnicalId == null) {
                // fallback to requested path id
                returnedTechnicalId = technicalId;
            }

            IngestionJobResponse response = new IngestionJobResponse();
            response.setTechnicalId(returnedTechnicalId);
            response.setSourceUrl(ingestionJob.getSourceUrl());
            response.setRequestedBy(ingestionJob.getRequestedBy());
            response.setStartedAt(ingestionJob.getStartedAt());
            response.setCompletedAt(ingestionJob.getCompletedAt());
            response.setStatus(ingestionJob.getStatus());
            response.setSummary(ingestionJob.getSummary());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid parameter for getIngestionJobById: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getIngestionJobById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving ingestion job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getIngestionJobById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    @Schema(name = "CreateIngestionJobRequest", description = "Request to create an ingestion job")
    public static class CreateIngestionJobRequest {
        @Schema(description = "Source URL for ingestion", example = "https://petstore.example/api/pets", required = true)
        private String sourceUrl;

        @Schema(description = "User or system requesting the job", example = "user-123", required = true)
        private String requestedBy;

        @Schema(description = "ISO-8601 timestamp when job started (optional, will be set if omitted)", example = "2025-08-01T12:00:00Z")
        private String startedAt;

        @Schema(description = "ISO-8601 timestamp when job completed (optional)", example = "2025-08-01T12:00:45Z")
        private String completedAt;

        @Schema(description = "Status of the job (optional, default PENDING)", example = "PENDING")
        private String status;
    }

    @Data
    @Schema(name = "IngestionJobResponse", description = "Response returned for ingestion job retrieval")
    public static class IngestionJobResponse {
        @Schema(description = "Technical identifier of the entity", example = "job-tech-0001")
        private String technicalId;

        @Schema(description = "Source URL for ingestion", example = "https://petstore.example/api/pets")
        private String sourceUrl;

        @Schema(description = "User or system requesting the job", example = "user-123")
        private String requestedBy;

        @Schema(description = "ISO-8601 timestamp when job started", example = "2025-08-01T12:00:00Z")
        private String startedAt;

        @Schema(description = "ISO-8601 timestamp when job completed", example = "2025-08-01T12:00:45Z")
        private String completedAt;

        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "Aggregated summary of the ingestion job results")
        private IngestionJob.Summary summary;
    }
}