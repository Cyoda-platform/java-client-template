package com.java_template.application.controller.ingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.common.service.EntityService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "IngestionJob", description = "APIs to manage IngestionJob orchestration entities (version 1)")
@RequiredArgsConstructor
public class IngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Create Ingestion Job", description = "Persist a new IngestionJob orchestration entity. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<CreateResponse> createIngestionJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Ingestion job creation payload", required = true, content = @Content(schema = @Schema(implementation = CreateIngestionJobRequest.class)))
            @RequestBody CreateIngestionJobRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            IngestionJob entity = new IngestionJob();
            entity.setJobId(request.getJobId());
            entity.setScheduleCron(request.getScheduleCron());
            entity.setSourceUrl(request.getSourceUrl());
            entity.setDataFormats(request.getDataFormats());
            entity.setNotifyEmail(request.getNotifyEmail());

            // Set initial status as PENDING as per orchestration contract (workflow will act on persisted entity)
            entity.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    IngestionJob.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();

            CreateResponse response = new CreateResponse();
            response.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create ingestion job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while creating ingestion job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating ingestion job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Ingestion Job by technicalId", description = "Retrieve persisted IngestionJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IngestionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<IngestionJobResponse> getIngestionJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;

            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            IngestionJobResponse response = objectMapper.treeToValue((JsonNode) node, IngestionJobResponse.class);

            // attempt to populate technicalId from meta if available
            try {
                if (dataPayload.getMeta() != null && dataPayload.getMeta().get("entityId") != null) {
                    response.setTechnicalId(dataPayload.getMeta().get("entityId").asText());
                } else {
                    response.setTechnicalId(technicalId);
                }
            } catch (Exception ignored) {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getIngestionJobById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while retrieving ingestion job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving ingestion job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    @Schema(name = "CreateIngestionJobRequest", description = "Request payload to create an IngestionJob")
    public static class CreateIngestionJobRequest {
        @Schema(description = "Natural/business identifier for the ingestion job", example = "weekly-petstore-ingest", required = true)
        private String jobId;

        @Schema(description = "Cron expression for scheduling", example = "0 9 * * MON")
        private String scheduleCron;

        @Schema(description = "Source URL to ingest from", example = "https://petstore.swagger.io", required = true)
        private String sourceUrl;

        @Schema(description = "Comma separated list of data formats, e.g. \"JSON,XML\"", example = "JSON,XML")
        private String dataFormats;

        @Schema(description = "Notification email for job run events", example = "victoria.sagdieva@cyoda.com")
        private String notifyEmail;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing only technicalId for created orchestration entities")
    public static class CreateResponse {
        @Schema(description = "Technical ID assigned to the persisted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "IngestionJobResponse", description = "Representation of persisted IngestionJob entity returned to clients")
    public static class IngestionJobResponse {
        @Schema(description = "Technical ID assigned to the persisted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Natural/business identifier for the ingestion job", example = "weekly-petstore-ingest")
        private String jobId;

        @Schema(description = "Cron expression for scheduling", example = "0 9 * * MON")
        private String scheduleCron;

        @Schema(description = "Source URL to ingest from", example = "https://petstore.swagger.io")
        private String sourceUrl;

        @Schema(description = "Comma separated list of data formats", example = "JSON,XML")
        private String dataFormats;

        @Schema(description = "ISO-8601 timestamp of the last run", example = "2025-08-25T09:00:00Z")
        private String lastRunAt;

        @Schema(description = "Job status, e.g. PENDING / RUNNING / COMPLETED / FAILED", example = "COMPLETED")
        private String status;

        @Schema(description = "Notification email for job run events", example = "victoria.sagdieva@cyoda.com")
        private String notifyEmail;
    }
}