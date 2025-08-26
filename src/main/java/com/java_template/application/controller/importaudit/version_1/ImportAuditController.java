package com.java_template.application.controller.importaudit.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importaudit.version_1.ImportAudit;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/v1/import-audits")
@Tag(name = "ImportAudit", description = "API for ImportAudit entity (version 1). Controller is a thin proxy to EntityService.")
public class ImportAuditController {

    private static final Logger logger = LoggerFactory.getLogger(ImportAuditController.class);

    private final EntityService entityService;

    public ImportAuditController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ImportAudit", description = "Persist a new ImportAudit entity. Controller performs basic request validation and proxies to EntityService.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createImportAudit(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportAudit payload", required = true, content = @Content(schema = @Schema(implementation = ImportAuditRequest.class)))
                                               @RequestBody ImportAuditRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("request body is required");
            }
            // Basic format validation (no business logic)
            if (request.getAuditId() == null || request.getAuditId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("auditId is required");
            }
            ImportAudit entity = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ImportAudit.ENTITY_NAME,
                    String.valueOf(ImportAudit.ENTITY_VERSION),
                    entity
            );
            UUID id = idFuture.get();
            AddResponse resp = new AddResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in createImportAudit", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createImportAudit", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createImportAudit", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createImportAudit", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple ImportAudits", description = "Persist multiple ImportAudit entities in batch.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddBatchResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createImportAuditsBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of ImportAudit payloads", required = true, content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportAuditRequest.class))))
                                                     @RequestBody List<ImportAuditRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("request body must contain at least one item");
            }
            List<ImportAudit> entities = new ArrayList<>();
            for (ImportAuditRequest r : requests) {
                if (r == null) continue;
                entities.add(toEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ImportAudit.ENTITY_NAME,
                    String.valueOf(ImportAudit.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = new ArrayList<>();
            for (UUID u : ids) technicalIds.add(u.toString());
            AddBatchResponse resp = new AddBatchResponse();
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in createImportAuditsBatch", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createImportAuditsBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createImportAuditsBatch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createImportAuditsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ImportAudit by technicalId", description = "Retrieve an ImportAudit by technical UUID returned by the platform.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportAuditResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getImportAudit(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportAudit.ENTITY_NAME,
                    String.valueOf(ImportAudit.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in getImportAudit", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getImportAudit", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getImportAudit", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getImportAudit", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all ImportAudits", description = "Retrieve all ImportAudit entities.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportAuditResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllImportAudits() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ImportAudit.ENTITY_NAME,
                    String.valueOf(ImportAudit.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllImportAudits", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getAllImportAudits", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getAllImportAudits", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search ImportAudits by condition", description = "Retrieve ImportAudit entities matching a simple search condition. Uses SearchConditionRequest for basic field-based queries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportAuditResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchImportAudits(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true, content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
                                              @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("condition is required");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ImportAudit.ENTITY_NAME,
                    String.valueOf(ImportAudit.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in searchImportAudits", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchImportAudits", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during searchImportAudits", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in searchImportAudits", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update ImportAudit", description = "Update an existing ImportAudit by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateImportAudit(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportAudit payload", required = true, content = @Content(schema = @Schema(implementation = ImportAuditRequest.class)))
            @RequestBody ImportAuditRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("technicalId is required");
            }
            if (request == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("request body is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            ImportAudit entity = toEntity(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    ImportAudit.ENTITY_NAME,
                    String.valueOf(ImportAudit.ENTITY_VERSION),
                    uuid,
                    entity
            );
            UUID updatedId = updatedIdFuture.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in updateImportAudit", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateImportAudit", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateImportAudit", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updateImportAudit", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete ImportAudit", description = "Delete an ImportAudit by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteImportAudit(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    ImportAudit.ENTITY_NAME,
                    String.valueOf(ImportAudit.ENTITY_VERSION),
                    uuid
            );
            UUID deletedId = deletedIdFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in deleteImportAudit", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteImportAudit", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteImportAudit", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteImportAudit", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper: convert request DTO to entity instance (no business logic)
    private ImportAudit toEntity(ImportAuditRequest request) {
        ImportAudit entity = new ImportAudit();
        entity.setAuditId(request.getAuditId());
        entity.setDetails(request.getDetails());
        entity.setHnId(request.getHnId());
        entity.setJobId(request.getJobId());
        entity.setOutcome(request.getOutcome());
        entity.setTimestamp(request.getTimestamp());
        return entity;
    }

    // Static DTO classes

    @Data
    public static class ImportAuditRequest {
        @Schema(description = "Unique audit id (technical)", required = true, example = "audit-abc-123")
        private String auditId;

        @Schema(description = "HN item id referenced by this audit", required = true, example = "12345")
        private Long hnId;

        @Schema(description = "ImportJob id that triggered this audit", required = true, example = "job-xyz")
        private String jobId;

        @Schema(description = "Outcome of the audit (SUCCESS or FAILURE)", required = true, example = "SUCCESS")
        private String outcome;

        @Schema(description = "Additional details or validation errors", required = false)
        private java.util.Map<String, Object> details;

        @Schema(description = "ISO-8601 timestamp for the audit", required = true, example = "2025-08-26T12:00:00Z")
        private String timestamp;
    }

    @Data
    public static class ImportAuditResponse {
        @Schema(description = "Technical audit id", example = "audit-abc-123")
        private String auditId;

        @Schema(description = "HN item id referenced by this audit", example = "12345")
        private Long hnId;

        @Schema(description = "ImportJob id that triggered this audit", example = "job-xyz")
        private String jobId;

        @Schema(description = "Outcome of the audit (SUCCESS or FAILURE)", example = "SUCCESS")
        private String outcome;

        @Schema(description = "Additional details or validation errors")
        private java.util.Map<String, Object> details;

        @Schema(description = "ISO-8601 timestamp for the audit", example = "2025-08-26T12:00:00Z")
        private String timestamp;
    }

    @Data
    public static class AddResponse {
        @Schema(description = "Technical id of the created entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;
    }

    @Data
    public static class AddBatchResponse {
        @Schema(description = "List of technical ids for created entities")
        private List<String> technicalIds;
    }

    @Data
    public static class UpdateResponse {
        @Schema(description = "Technical id of the updated entity")
        private String technicalId;
    }

    @Data
    public static class DeleteResponse {
        @Schema(description = "Technical id of the deleted entity")
        private String technicalId;
    }
}