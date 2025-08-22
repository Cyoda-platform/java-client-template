package com.java_template.application.controller.importjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import lombok.Data;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/import-job/v1")
@Tag(name = "ImportJob", description = "API for ImportJob entity (version 1)")
public class ImportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);

    private final EntityService entityService;

    public ImportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ImportJob", description = "Add a single ImportJob entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createImportJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportJob payload")
            @RequestBody CreateRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request body is missing");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    request.getData()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new CreateResponse(id));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request (execution): {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple ImportJobs", description = "Add multiple ImportJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateManyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createImportJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of ImportJob payloads")
            @RequestBody CreateManyRequest request) {
        try {
            if (request == null || request.getData() == null || request.getData().isEmpty()) {
                throw new IllegalArgumentException("Request body is missing or empty");
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    request.getData()
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new CreateManyResponse(ids));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request (execution): {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all ImportJobs", description = "Retrieve all ImportJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllImportJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request (execution): {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ImportJob by id", description = "Retrieve a single ImportJob by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getImportJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request (execution): {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter ImportJobs", description = "Retrieve ImportJob entities by a simple field filter")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> filterImportJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Filter request")
            @RequestBody FilterRequest request) {
        try {
            if (request == null || request.getField() == null || request.getOperator() == null) {
                throw new IllegalArgumentException("Invalid filter request");
            }
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of(String.format("$.%s", request.getField()), request.getOperator(), request.getValue())
            );
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request (execution): {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update ImportJob", description = "Update an existing ImportJob by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Update payload")
            @RequestBody UpdateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request body is missing");
            }
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    request.getData()
            );
            UUID id = updatedId.get();
            return ResponseEntity.ok(new CreateResponse(id));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request (execution): {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete ImportJob", description = "Delete an ImportJob by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            return ResponseEntity.ok(new CreateResponse(id));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request (execution): {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Static DTO classes for request/response payloads

    @Data
    @Schema(name = "CreateRequest", description = "Request to create a single ImportJob")
    public static class CreateRequest {
        @Schema(description = "ImportJob entity JSON", required = true, implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "CreateManyRequest", description = "Request to create multiple ImportJobs")
    public static class CreateManyRequest {
        @Schema(description = "List of ImportJob entity JSONs", required = true, implementation = ObjectNode.class)
        private List<ObjectNode> data;
    }

    @Data
    @Schema(name = "UpdateRequest", description = "Request to update an ImportJob")
    public static class UpdateRequest {
        @Schema(description = "ImportJob entity JSON with updated fields", required = true, implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "FilterRequest", description = "Simple filter request")
    public static class FilterRequest {
        @Schema(description = "Field name to filter on", required = true, example = "status")
        private String field;

        @Schema(description = "Operator to use (EQUALS, NOT_EQUAL, etc.)", required = true, example = "EQUALS")
        private String operator;

        @Schema(description = "Value to compare", required = false, example = "COMPLETED")
        private String value;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Generic response with single UUID")
    public static class CreateResponse {
        @Schema(description = "Technical id", required = true)
        private UUID id;

        public CreateResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "CreateManyResponse", description = "Response with multiple UUIDs")
    public static class CreateManyResponse {
        @Schema(description = "List of technical ids", required = true)
        private List<UUID> ids;

        public CreateManyResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }
}