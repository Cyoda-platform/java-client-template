package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/import-jobs")
@Tag(name = "ImportJob Controller", description = "Proxy endpoints for ImportJob entity")
public class ImportJobController {
    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ImportJob", description = "Create a new ImportJob record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportJob payload") @RequestBody ImportJobRequest request) {
        try {
            logger.info("Create ImportJob request: {}", request);
            if (request == null) return badRequest("validation_error", "Request body is required");
            if (request.getJobId() == null || request.getJobId().isBlank()) return badRequest("validation_error", "jobId is required");
            if (request.getItemId() == null || request.getItemId().isBlank()) return badRequest("validation_error", "itemId is required");
            if (request.getStatus() == null || request.getStatus().isBlank()) return badRequest("validation_error", "status is required");

            ImportJob entity = new ImportJob();
            entity.setJobId(request.getJobId());
            entity.setItemId(request.getItemId());
            entity.setStatus(request.getStatus());
            if (request.getCreatedAt() != null) entity.setCreatedAt(request.getCreatedAt());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    entity
            );
            java.util.UUID technicalId = idFuture.get();

            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId);
            resp.setJobId(entity.getJobId());

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(String.format("/import-jobs/%s", technicalId.toString())));
            return ResponseEntity.status(201).headers(headers).body(resp);

        } catch (IllegalArgumentException iae) {
            return badRequest("validation_error", iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(errorBody("not_found", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    @Operation(summary = "Get list of ImportJobs", description = "Retrieve all ImportJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list() {
        try {
            ArrayNode items = entityService.getItems(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION)
            ).get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    @Operation(summary = "Search ImportJobs", description = "Search ImportJobs by jobId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(@Parameter(description = "jobId to search for") @RequestParam(required = false) String jobId) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return list();
            }
            ArrayNode found = entityService.getItemsByCondition(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.jobId", "EQUALS", jobId)),
                    true
            ).get();
            return ResponseEntity.ok(found);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    @Operation(summary = "Get ImportJob by technical id", description = "Retrieve a single ImportJob by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getById(@Parameter(name = "technicalId", description = "Technical UUID of the ImportJob") @PathVariable String technicalId) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    uuid
            ).get();
            if (node == null) {
                return ResponseEntity.status(404).body(errorBody("not_found", "No ImportJob with requested id"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            return badRequest("validation_error", iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(errorBody("not_found", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    @Operation(summary = "Update ImportJob", description = "Update an existing ImportJob by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@Parameter(name = "technicalId", description = "Technical UUID of the ImportJob") @PathVariable String technicalId,
                                    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportJob payload") @RequestBody ImportJobRequest request) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(technicalId);
            if (request == null) return badRequest("validation_error", "Request body is required");
            if (request.getJobId() == null || request.getJobId().isBlank()) return badRequest("validation_error", "jobId is required");
            if (request.getItemId() == null || request.getItemId().isBlank()) return badRequest("validation_error", "itemId is required");
            if (request.getStatus() == null || request.getStatus().isBlank()) return badRequest("validation_error", "status is required");

            ImportJob entity = new ImportJob();
            entity.setJobId(request.getJobId());
            entity.setItemId(request.getItemId());
            entity.setStatus(request.getStatus());
            if (request.getCreatedAt() != null) entity.setCreatedAt(request.getCreatedAt());

            CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    uuid,
                    entity
            );
            java.util.UUID updatedId = updated.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId);
            resp.setJobId(entity.getJobId());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            return badRequest("validation_error", iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(errorBody("not_found", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    @Operation(summary = "Delete ImportJob", description = "Delete an ImportJob by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}")
    public ResponseEntity<?> delete(@Parameter(name = "technicalId", description = "Technical UUID of the ImportJob") @PathVariable String technicalId) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(technicalId);
            CompletableFuture<java.util.UUID> deleted = entityService.deleteItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    uuid
            );
            java.util.UUID deletedId = deleted.get();
            ObjectNode resp = objectMapper.createObjectNode();
            resp.put("deletedTechnicalId", deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            return badRequest("validation_error", iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(errorBody("not_found", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    private ResponseEntity<Object> badRequest(String error, String message) {
        return ResponseEntity.badRequest().body(errorBody(error, message));
    }

    private ObjectNode errorBody(String error, String message) {
        ObjectNode obj = objectMapper.createObjectNode();
        obj.put("error", error == null ? "server_error" : error);
        obj.put("message", message == null ? "" : message);
        return obj;
    }

    @Data
    static class ImportJobRequest {
        @Schema(description = "Job id (business id) for the import job")
        private String jobId;

        @Schema(description = "Reference to HackerNews item id")
        private String itemId;

        @Schema(description = "Status of the job, e.g., PENDING, COMPLETED")
        private String status;

        @Schema(description = "Creation timestamp (epoch millis)")
        private Long createdAt;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical UUID assigned to the created entity")
        private java.util.UUID technicalId;

        @Schema(description = "Business jobId of the created ImportJob")
        private String jobId;
    }

    @Data
    static class UpdateResponse {
        @Schema(description = "Technical UUID of the updated entity")
        private java.util.UUID technicalId;

        @Schema(description = "Business jobId of the updated ImportJob")
        private String jobId;
    }
}
