package com.java_template.application.controller.ingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
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

    @Operation(summary = "Create multiple Ingestion Jobs", description = "Persist multiple IngestionJob orchestration entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BulkCreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<BulkCreateResponse> createIngestionJobsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of ingestion job creation payloads", required = true)
            @RequestBody List<CreateIngestionJobRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one item");
            }

            List<IngestionJob> entities = new ArrayList<>();
            for (CreateIngestionJobRequest req : requests) {
                IngestionJob entity = new IngestionJob();
                entity.setJobId(req.getJobId());
                entity.setScheduleCron(req.getScheduleCron());
                entity.setSourceUrl(req.getSourceUrl());
                entity.setDataFormats(req.getDataFormats());
                entity.setNotifyEmail(req.getNotifyEmail());
                entity.setStatus("PENDING");
                entities.add(entity);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    IngestionJob.ENTITY_NAME,
                    IngestionJob.ENTITY_VERSION,
                    entities
            );
            List<UUID> entityIds = idsFuture.get();

            BulkCreateResponse response = new BulkCreateResponse();
            List<String> technicalIds = new ArrayList<>();
            if (entityIds != null) {
                for (UUID id : entityIds) {
                    technicalIds.add(id.toString());
                }
            }
            response.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk create request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while creating ingestion jobs in bulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating ingestion jobs in bulk", ex);
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

    @Operation(summary = "Get all Ingestion Jobs", description = "Retrieve all persisted IngestionJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IngestionJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<IngestionJobResponse>> getAllIngestionJobs() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    IngestionJob.ENTITY_NAME,
                    IngestionJob.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<IngestionJobResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    IngestionJobResponse resp = objectMapper.treeToValue(data, IngestionJobResponse.class);
                    // attempt to set technicalId from meta
                    try {
                        if (payload.getMeta() != null && payload.getMeta().get("entityId") != null) {
                            resp.setTechnicalId(payload.getMeta().get("entityId").asText());
                        }
                    } catch (Exception ignored) { }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while retrieving ingestion jobs list", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving ingestion jobs list", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search Ingestion Jobs by condition", description = "Retrieve IngestionJob entities matching a search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IngestionJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<List<IngestionJobResponse>> searchIngestionJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true)
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    IngestionJob.ENTITY_NAME,
                    IngestionJob.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<IngestionJobResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    IngestionJobResponse resp = objectMapper.treeToValue(data, IngestionJobResponse.class);
                    try {
                        if (payload.getMeta() != null && payload.getMeta().get("entityId") != null) {
                            resp.setTechnicalId(payload.getMeta().get("entityId").asText());
                        }
                    } catch (Exception ignored) { }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while searching ingestion jobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while searching ingestion jobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update Ingestion Job", description = "Update persisted IngestionJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<CreateResponse> updateIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Ingestion job update payload", required = true, content = @Content(schema = @Schema(implementation = CreateIngestionJobRequest.class)))
            @RequestBody CreateIngestionJobRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            IngestionJob entity = new IngestionJob();
            entity.setJobId(request.getJobId());
            entity.setScheduleCron(request.getScheduleCron());
            entity.setSourceUrl(request.getSourceUrl());
            entity.setDataFormats(request.getDataFormats());
            entity.setNotifyEmail(request.getNotifyEmail());
            // status is optional to update; preserve if not provided - controller must not implement business logic,
            // so accept status only if included in request by reusing same CreateIngestionJobRequest structure (no status field).
            // If business requires status updates, a dedicated DTO should be used.

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    UUID.fromString(technicalId),
                    entity
            );
            UUID entityId = updatedId.get();

            CreateResponse response = new CreateResponse();
            response.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update ingestion job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while updating ingestion job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while updating ingestion job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete Ingestion Job", description = "Delete persisted IngestionJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<CreateResponse> deleteIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID entityId = deletedId.get();

            CreateResponse response = new CreateResponse();
            response.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete ingestion job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while deleting ingestion job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting ingestion job", ex);
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
    @Schema(name = "BulkCreateResponse", description = "Response containing technicalIds for created orchestration entities")
    public static class BulkCreateResponse {
        @Schema(description = "Technical IDs assigned to the persisted entities")
        private List<String> technicalIds;
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