package com.java_template.application.controller.ingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/ingestion-jobs")
@Tag(name = "IngestionJob", description = "Controller for IngestionJob entity (version 1)")
public class IngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobController.class);

    private final EntityService entityService;

    public IngestionJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create IngestionJob", description = "Create a new IngestionJob orchestration entity. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createIngestionJob(
            @RequestBody(description = "IngestionJob create request", required = true, content = @Content(schema = @Schema(implementation = IngestionJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody IngestionJobRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            IngestionJob entity = toEntityForCreate(request);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    entity
            );

            UUID id = idFuture.get();

            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createIngestionJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createIngestionJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get IngestionJob by technicalId", description = "Retrieve an IngestionJob by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IngestionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            }

            IngestionJobResponse resp = fromObjectNode(node);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getIngestionJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getIngestionJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all IngestionJobs", description = "Retrieve all IngestionJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IngestionJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllIngestionJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            List<IngestionJobResponse> out = new ArrayList<>();
            if (array != null) {
                for (JsonNode n : array) {
                    if (n.isObject()) {
                        out.add(fromObjectNode((ObjectNode) n));
                    }
                }
            }
            return ResponseEntity.ok(out);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllIngestionJobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAllIngestionJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getAllIngestionJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update IngestionJob", description = "Update an existing IngestionJob by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @RequestBody(description = "IngestionJob update request", required = true, content = @Content(schema = @Schema(implementation = IngestionJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody IngestionJobRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            IngestionJob entity = toEntityForUpdate(request);
            CompletableFuture<UUID> updated = entityService.updateItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    entity
            );

            UUID id = updated.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateIngestionJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateIngestionJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updateIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete IngestionJob", description = "Delete an IngestionJob by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID id = deleted.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for deleteIngestionJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteIngestionJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in deleteIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteIngestionJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Helpers and DTO mappings ---

    private IngestionJob toEntityForCreate(IngestionJobRequest req) {
        IngestionJob e = new IngestionJob();
        // ensure required fields for entity validation are present: createdAt and status
        e.setCreatedAt(Instant.now().toString());
        e.setStatus("PENDING");
        e.setCreatedBy(req.getCreated_by());
        e.setSourceUrl(req.getSource_url());
        e.setDataFormats(req.getData_formats());
        e.setScheduleDay(req.getSchedule_day());
        e.setScheduleTime(req.getSchedule_time());
        e.setTimeWindowDays(req.getTime_window_days());
        return e;
    }

    private IngestionJob toEntityForUpdate(IngestionJobRequest req) {
        IngestionJob e = new IngestionJob();
        // For updates, setable fields are taken from request; do not change createdAt/status unless provided.
        // If not provided, set minimal values to satisfy entity validation.
        e.setCreatedBy(req.getCreated_by() != null ? req.getCreated_by() : "unknown");
        e.setCreatedAt(Instant.now().toString());
        e.setStatus("PENDING");
        e.setSourceUrl(req.getSource_url());
        e.setDataFormats(req.getData_formats());
        e.setScheduleDay(req.getSchedule_day());
        e.setScheduleTime(req.getSchedule_time());
        e.setTimeWindowDays(req.getTime_window_days());
        return e;
    }

    private IngestionJobResponse fromObjectNode(ObjectNode node) {
        IngestionJobResponse r = new IngestionJobResponse();
        r.setTechnicalId(getText(node, "id"));
        r.setSource_url(getText(node, "sourceUrl"));
        r.setSchedule_day(getText(node, "scheduleDay"));
        r.setSchedule_time(getText(node, "scheduleTime"));
        r.setData_formats(getText(node, "dataFormats"));
        r.setTime_window_days(getInteger(node, "timeWindowDays"));
        r.setStatus(getText(node, "status"));
        r.setCreated_by(getText(node, "createdBy"));
        r.setCreated_at(getText(node, "createdAt"));
        return r;
    }

    private String getText(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        return n.asText();
    }

    private Integer getInteger(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull() || !n.canConvertToInt()) return null;
        return n.asInt();
    }

    // --- DTOs ---

    @Data
    @Schema(name = "IngestionJobRequest", description = "Request payload to create or update an IngestionJob")
    public static class IngestionJobRequest {
        @Schema(description = "Source URL for ingestion", example = "https://petstore.swagger.io/v2/store")
        private String source_url;

        @Schema(description = "Day of schedule", example = "Monday")
        private String schedule_day;

        @Schema(description = "Time of schedule", example = "08:00")
        private String schedule_time;

        @Schema(description = "Comma separated data formats", example = "JSON,XML")
        private String data_formats;

        @Schema(description = "Time window in days", example = "7")
        private Integer time_window_days;

        @Schema(description = "User who created the job", example = "admin@example.com")
        private String created_by;
    }

    @Data
    @Schema(name = "IngestionJobResponse", description = "Response payload for IngestionJob retrieval")
    public static class IngestionJobResponse {
        @Schema(description = "Technical ID of the entity", example = "ingest-12345")
        private String technicalId;

        @Schema(description = "Source URL for ingestion", example = "https://petstore.swagger.io/v2/store")
        private String source_url;

        @Schema(description = "Day of schedule", example = "Monday")
        private String schedule_day;

        @Schema(description = "Time of schedule", example = "08:00")
        private String schedule_time;

        @Schema(description = "Comma separated data formats", example = "JSON,XML")
        private String data_formats;

        @Schema(description = "Time window in days", example = "7")
        private Integer time_window_days;

        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "User who created the job", example = "admin@example.com")
        private String created_by;

        @Schema(description = "Creation timestamp", example = "2025-08-25T08:00:00Z")
        private String created_at;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response with technicalId only")
    public static class CreateResponse {
        @Schema(description = "Technical ID of the created/affected entity", example = "ingest-12345")
        private String technicalId;
    }
}