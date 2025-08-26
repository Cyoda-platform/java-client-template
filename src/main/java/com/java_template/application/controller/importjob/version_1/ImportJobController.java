package com.java_template.application.controller.importjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/import-jobs")
@Tag(name = "ImportJob", description = "ImportJob entity operations (version 1) - controller is a thin proxy to EntityService")
public class ImportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ImportJob", description = "Creates a new ImportJob event. Returns technicalId of the created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createImportJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportJob create request", required = true,
            content = @Content(schema = @Schema(implementation = ImportJobCreateRequest.class)))
                                             @RequestBody ImportJobCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getJobType() == null || request.getJobType().isBlank()) {
                throw new IllegalArgumentException("jobType is required");
            }
            if (request.getSourceReference() == null || request.getSourceReference().isBlank()) {
                throw new IllegalArgumentException("sourceReference is required");
            }

            // Build minimal ImportJob entity representation
            ImportJob job = new ImportJob();
            job.setJobId(UUID.randomUUID().toString());
            job.setJobType(request.getJobType());
            job.setSourceReference(request.getSourceReference());
            job.setStatus("Pending");
            job.setCreatedAt(Instant.now().toString());
            ImportJob.ResultSummary summary = new ImportJob.ResultSummary();
            summary.setCreated(0);
            summary.setUpdated(0);
            summary.setFailed(0);
            job.setResultSummary(summary);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            ImportJobCreateResponse resp = new ImportJobCreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create ImportJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during create ImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during create ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during create ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple ImportJobs", description = "Creates multiple ImportJob events. Returns list of technicalIds of created entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobBulkCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createImportJobsBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of ImportJob create requests", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportJobCreateRequest.class))))
                                                 @RequestBody List<ImportJobCreateRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list is required");
            }
            List<ImportJob> jobs = new ArrayList<>();
            for (ImportJobCreateRequest request : requests) {
                if (request.getJobType() == null || request.getJobType().isBlank()) {
                    throw new IllegalArgumentException("jobType is required for each item");
                }
                if (request.getSourceReference() == null || request.getSourceReference().isBlank()) {
                    throw new IllegalArgumentException("sourceReference is required for each item");
                }
                ImportJob job = new ImportJob();
                job.setJobId(UUID.randomUUID().toString());
                job.setJobType(request.getJobType());
                job.setSourceReference(request.getSourceReference());
                job.setStatus("Pending");
                job.setCreatedAt(Instant.now().toString());
                ImportJob.ResultSummary summary = new ImportJob.ResultSummary();
                summary.setCreated(0);
                summary.setUpdated(0);
                summary.setFailed(0);
                job.setResultSummary(summary);
                jobs.add(job);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    jobs
            );

            List<UUID> ids = idsFuture.get();
            ImportJobBulkCreateResponse resp = new ImportJobBulkCreateResponse();
            List<String> stringIds = new ArrayList<>();
            for (UUID id : ids) stringIds.add(id.toString());
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk create request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during bulk create operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during bulk create operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during bulk create ImportJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during bulk create ImportJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during bulk create ImportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ImportJob by technicalId", description = "Retrieves the full ImportJob entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJob.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ImportJob not found");
            }
            ImportJob job = objectMapper.treeToValue(node, ImportJob.class);
            return ResponseEntity.ok(job);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get ImportJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("ImportJob not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument while retrieving ImportJob: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during get ImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during get ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during get ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all ImportJobs", description = "Retrieves all ImportJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportJob.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllImportJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            List<ImportJob> result = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    ImportJob job = objectMapper.treeToValue(node, ImportJob.class);
                    result.add(job);
                }
            }
            return ResponseEntity.ok(result);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument while retrieving ImportJobs: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during get all ImportJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during get all ImportJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during get all ImportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search ImportJobs by simple filter", description = "Search ImportJobs by a single field condition (AND group).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportJob.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchImportJobs(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Filter request", required = true,
            content = @Content(schema = @Schema(implementation = ImportJobFilterRequest.class)))
                                            @RequestBody ImportJobFilterRequest filter) {
        try {
            if (filter == null) {
                throw new IllegalArgumentException("Filter body is required");
            }
            if (filter.getField() == null || filter.getField().isBlank()) {
                throw new IllegalArgumentException("field is required");
            }
            if (filter.getOperator() == null || filter.getOperator().isBlank()) {
                throw new IllegalArgumentException("operator is required");
            }
            if (filter.getValue() == null) {
                throw new IllegalArgumentException("value is required");
            }

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + filter.getField(), filter.getOperator(), filter.getValue().toString())
            );

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredItemsFuture.get();
            List<ImportJob> result = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    ImportJob job = objectMapper.treeToValue(node, ImportJob.class);
                    result.add(job);
                }
            }
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument while searching ImportJobs: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during search ImportJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during search ImportJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during search ImportJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update ImportJob", description = "Updates an ImportJob entity by technicalId. Returns technicalId of updated entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobUpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportJob update request", required = true,
                    content = @Content(schema = @Schema(implementation = ImportJobUpdateRequest.class)))
            @RequestBody ImportJobUpdateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            UUID id = UUID.fromString(technicalId);

            // Build ImportJob entity from request (controller must not implement business validation)
            ImportJob job = new ImportJob();
            // If client provided jobId keep it, else leave null - business logic may handle it
            job.setJobId(request.getJobId());
            job.setJobType(request.getJobType());
            job.setSourceReference(request.getSourceReference());
            job.setStatus(request.getStatus());
            job.setCreatedAt(request.getCreatedAt());
            if (request.getResultSummary() != null) {
                ImportJob.ResultSummary rs = new ImportJob.ResultSummary();
                rs.setCreated(request.getResultSummary().getCreated());
                rs.setUpdated(request.getResultSummary().getUpdated());
                rs.setFailed(request.getResultSummary().getFailed());
                job.setResultSummary(rs);
            }

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    id,
                    job
            );

            UUID updatedId = updatedIdFuture.get();
            ImportJobUpdateResponse resp = new ImportJobUpdateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update ImportJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("ImportJob not found during update: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during update operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during update ImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during update ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during update ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete ImportJob", description = "Deletes an ImportJob entity by technicalId. Returns technicalId of deleted entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobDeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            ImportJobDeleteResponse resp = new ImportJobDeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete ImportJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("ImportJob not found during delete: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during delete operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during delete ImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during delete ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during delete ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    static class ImportJobCreateRequest {
        @Schema(description = "Type of import job (e.g., products or users)", required = true, example = "products")
        private String jobType;

        @Schema(description = "Source reference (file path or external ref)", required = true, example = "s3://bucket/file.csv")
        private String sourceReference;
    }

    @Data
    static class ImportJobCreateResponse {
        @Schema(description = "Technical ID of the created ImportJob", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    static class ImportJobBulkCreateResponse {
        @Schema(description = "Technical IDs of created ImportJobs")
        private List<String> technicalIds;
    }

    @Data
    static class ImportJobFilterRequest {
        @Schema(description = "Field name to filter on (without $.)", example = "jobType", required = true)
        private String field;

        @Schema(description = "Operator to use (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, etc.)", example = "EQUALS", required = true)
        private String operator;

        @Schema(description = "Value to compare", required = true)
        private Object value;
    }

    @Data
    static class ImportJobUpdateRequest {
        @Schema(description = "Optional jobId (business may ignore)", example = "job-123")
        private String jobId;

        @Schema(description = "Type of import job (e.g., products or users)", example = "products")
        private String jobType;

        @Schema(description = "Source reference (file path or external ref)", example = "s3://bucket/file.csv")
        private String sourceReference;

        @Schema(description = "Status of the import job", example = "Completed")
        private String status;

        @Schema(description = "Created at ISO timestamp", example = "2025-08-26T15:00:00Z")
        private String createdAt;

        @Schema(description = "Result summary")
        private ResultSummaryDto resultSummary;
    }

    @Data
    static class ResultSummaryDto {
        @Schema(description = "Number created", example = "10")
        private Integer created;
        @Schema(description = "Number updated", example = "2")
        private Integer updated;
        @Schema(description = "Number failed", example = "1")
        private Integer failed;
    }

    @Data
    static class ImportJobUpdateResponse {
        @Schema(description = "Technical ID of the updated ImportJob", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    static class ImportJobDeleteResponse {
        @Schema(description = "Technical ID of the deleted ImportJob", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}