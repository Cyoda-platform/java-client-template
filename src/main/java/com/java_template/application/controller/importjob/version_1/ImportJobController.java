package com.java_template.application.controller.importjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/import-jobs/v1")
@Tag(name = "ImportJob", description = "Controller for ImportJob entity (version 1). Proxy to EntityService only.")
public class ImportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    // -------------------------
    // Create single ImportJob
    // -------------------------
    @Operation(summary = "Create ImportJob", description = "Create an ImportJob orchestration event. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createImportJob(
            @RequestBody CreateImportJobRequest request
    ) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            // Map request to entity (simple field copy; no business logic)
            ImportJob entity = new ImportJob();
            entity.setSourceUrl(request.getSourceUrl());
            entity.setMode(request.getMode());
            entity.setRequestedBy(request.getRequestedBy());
            entity.setNotes(request.getNotes());
            // other entity fields (jobId, createdAt, status, processedCount, failedCount) are left to workflows

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    entity
            );
            java.util.UUID technicalId = idFuture.get();

            CreateImportJobResponse resp = new CreateImportJobResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createImportJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // Create multiple ImportJobs (bulk)
    // -------------------------
    @Operation(summary = "Bulk create ImportJobs", description = "Create multiple ImportJob orchestration events. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateImportJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createImportJobsBulk(
            @RequestBody List<CreateImportJobRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body must contain a non-empty list");

            List<ImportJob> entities = new ArrayList<>();
            for (CreateImportJobRequest r : requests) {
                ImportJob e = new ImportJob();
                e.setSourceUrl(r.getSourceUrl());
                e.setMode(r.getMode());
                e.setRequestedBy(r.getRequestedBy());
                e.setNotes(r.getNotes());
                entities.add(e);
            }

            CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    entities
            );

            List<java.util.UUID> ids = idsFuture.get();
            List<CreateImportJobResponse> resp = new ArrayList<>();
            for (java.util.UUID id : ids) {
                CreateImportJobResponse r = new CreateImportJobResponse();
                r.setTechnicalId(id.toString());
                resp.add(r);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createImportJobsBulk: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createImportJobsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ImportJobs bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createImportJobsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // Get ImportJob by technicalId
    // -------------------------
    @Operation(summary = "Get ImportJob by technicalId", description = "Retrieve a stored ImportJob by its technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getImportJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");

            ImportJobResponse resp = mapper.convertValue(node, ImportJobResponse.class);
            // ensure technicalId is present in response
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getImportJobById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getImportJobById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getImportJobById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // Get all ImportJobs
    // -------------------------
    @Operation(summary = "Get all ImportJobs", description = "Retrieve all stored ImportJobs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllImportJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<ImportJobResponse> resp = new ArrayList<>();
            if (array != null) {
                array.forEach(node -> resp.add(mapper.convertValue(node, ImportJobResponse.class)));
            }
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllImportJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all ImportJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getAllImportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // Search ImportJobs by condition (in-memory)
    // -------------------------
    @Operation(summary = "Search ImportJobs", description = "Search ImportJobs by condition (simple field based conditions). Uses in-memory filtering.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchImportJobs(
            @RequestBody SearchConditionRequest condition
    ) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = itemsFuture.get();
            List<ImportJobResponse> resp = new ArrayList<>();
            if (array != null) {
                array.forEach(node -> resp.add(mapper.convertValue(node, ImportJobResponse.class)));
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchImportJobs: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchImportJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching ImportJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in searchImportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // Update ImportJob by technicalId
    // -------------------------
    @Operation(summary = "Update ImportJob", description = "Update an existing ImportJob by technicalId. Returns technicalId of updated entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody UpdateImportJobRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            ImportJob entity = new ImportJob();
            entity.setJobId(request.getJobId());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setSourceUrl(request.getSourceUrl());
            entity.setMode(request.getMode());
            entity.setRequestedBy(request.getRequestedBy());
            entity.setStatus(request.getStatus());
            entity.setProcessedCount(request.getProcessedCount());
            entity.setFailedCount(request.getFailedCount());
            entity.setNotes(request.getNotes());

            CompletableFuture<java.util.UUID> updatedFuture = entityService.updateItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    entity
            );
            java.util.UUID updatedId = updatedFuture.get();
            UpdateImportJobResponse resp = new UpdateImportJobResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateImportJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updateImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // Delete ImportJob by technicalId
    // -------------------------
    @Operation(summary = "Delete ImportJob", description = "Delete an ImportJob by technicalId. Returns technicalId of deleted entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<java.util.UUID> deletedFuture = entityService.deleteItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            java.util.UUID deletedId = deletedFuture.get();
            DeleteImportJobResponse resp = new DeleteImportJobResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteImportJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // DTOs
    // -------------------------
    @Data
    @Schema(name = "CreateImportJobRequest", description = "Payload to create an ImportJob orchestration event.")
    static class CreateImportJobRequest {
        @Schema(description = "Source URL for the import", example = "https://petstore.example/api/pets")
        private String sourceUrl;

        @Schema(description = "Mode of import (full/incremental)", example = "full")
        private String mode;

        @Schema(description = "Actor who requested the job", example = "admin@purrfectpets.local")
        private String requestedBy;

        @Schema(description = "Notes or diagnostic info", example = "Initial import")
        private String notes;
    }

    @Data
    @Schema(name = "CreateImportJobResponse", description = "Response with technicalId for created ImportJob.")
    static class CreateImportJobResponse {
        @Schema(description = "Technical ID of the created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "ImportJobResponse", description = "Representation of ImportJob returned to clients.")
    static class ImportJobResponse {
        @Schema(description = "Technical ID of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Business job id", example = "import-2025-08-26-01")
        private String jobId;

        @Schema(description = "Source URL", example = "https://petstore.example/api/pets")
        private String sourceUrl;

        @Schema(description = "Mode of import", example = "full")
        private String mode;

        @Schema(description = "Actor who requested the job", example = "admin@purrfectpets.local")
        private String requestedBy;

        @Schema(description = "ISO-8601 creation timestamp", example = "2025-08-26T10:00:00Z")
        private String createdAt;

        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "Number of processed records", example = "120")
        private Integer processedCount;

        @Schema(description = "Number of failed records", example = "2")
        private Integer failedCount;

        @Schema(description = "Diagnostic notes", example = "Imported successfully with 2 minor issues")
        private String notes;
    }

    @Data
    @Schema(name = "UpdateImportJobRequest", description = "Payload to update an ImportJob.")
    static class UpdateImportJobRequest {
        @Schema(description = "Business job id", example = "import-2025-08-26-01")
        private String jobId;

        @Schema(description = "ISO-8601 creation timestamp", example = "2025-08-26T10:00:00Z")
        private String createdAt;

        @Schema(description = "Source URL", example = "https://petstore.example/api/pets")
        private String sourceUrl;

        @Schema(description = "Mode of import", example = "full")
        private String mode;

        @Schema(description = "Actor who requested the job", example = "admin@purrfectpets.local")
        private String requestedBy;

        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "Number of processed records", example = "120")
        private Integer processedCount;

        @Schema(description = "Number of failed records", example = "2")
        private Integer failedCount;

        @Schema(description = "Diagnostic notes", example = "Imported successfully with 2 minor issues")
        private String notes;
    }

    @Data
    @Schema(name = "UpdateImportJobResponse", description = "Response with technicalId for updated ImportJob.")
    static class UpdateImportJobResponse {
        @Schema(description = "Technical ID of the updated entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteImportJobResponse", description = "Response with technicalId for deleted ImportJob.")
    static class DeleteImportJobResponse {
        @Schema(description = "Technical ID of the deleted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}