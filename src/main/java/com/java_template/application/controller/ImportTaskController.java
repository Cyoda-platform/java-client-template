package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importtask.version_1.ImportTask;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/import-tasks")
@Tag(name = "ImportTask")
public class ImportTaskController {
    private static final Logger logger = LoggerFactory.getLogger(ImportTaskController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ImportTaskController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create ImportTask", description = "Create an ImportTask (typically created by ImportJob processing). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createImportTask(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ImportTask create request") @RequestBody CreateImportTaskRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("request body is required");
            if (request.getJobTechnicalId() == null || request.getJobTechnicalId().isBlank()) throw new IllegalArgumentException("jobTechnicalId is required");

            ImportTask task = new ImportTask();
            task.setJobTechnicalId(request.getJobTechnicalId());
            task.setHnItemId(request.getHnItemId());
            task.setStatus("QUEUED");
            task.setAttempts(request.getAttempts() == null ? 0 : request.getAttempts());
            task.setErrorMessage(request.getErrorMessage());
            task.setCreatedAt(Instant.now());
            task.setLastUpdatedAt(Instant.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ImportTask.ENTITY_NAME,
                    String.valueOf(ImportTask.ENTITY_VERSION),
                    task
            );
            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating ImportTask", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ImportTask", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating ImportTask", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ImportTask by technicalId", description = "Retrieve an ImportTask by its datastore technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(path = "/{technicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getByTechnicalId(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportTask.ENTITY_NAME,
                    String.valueOf(ImportTask.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ImportTask not found");
            }
            ImportTaskResponse resp = mapNodeToResponse(node);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching ImportTask", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching ImportTask", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching ImportTask", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ImportTasks by jobTechnicalId", description = "Retrieve all ImportTasks associated with a given ImportJob technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImportTaskResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(path = "/job/{jobTechnicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getByJobTechnicalId(@Parameter(name = "jobTechnicalId", description = "Technical ID of the job") @PathVariable String jobTechnicalId) {
        try {
            if (jobTechnicalId == null || jobTechnicalId.isBlank()) throw new IllegalArgumentException("jobTechnicalId is required");
            SearchConditionRequest condition = SearchConditionRequest.group("AND", Condition.of("$.jobTechnicalId", "EQUALS", jobTechnicalId));
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    ImportTask.ENTITY_NAME,
                    String.valueOf(ImportTask.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode results = itemsFuture.get();
            List<ImportTaskResponse> list = new ArrayList<>();
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    ObjectNode node = (ObjectNode) results.get(i);
                    ImportTaskResponse resp = mapNodeToResponse(node);
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching ImportTasks", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching ImportTasks", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching ImportTasks", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ImportTaskResponse mapNodeToResponse(ObjectNode node) {
        ImportTaskResponse resp = new ImportTaskResponse();
        if (node.hasNonNull("jobTechnicalId")) resp.setJobTechnicalId(node.get("jobTechnicalId").asText());
        if (node.hasNonNull("hnItemId")) resp.setHnItemId(node.get("hnItemId").asLong());
        if (node.hasNonNull("status")) resp.setStatus(node.get("status").asText());
        if (node.hasNonNull("attempts")) resp.setAttempts(node.get("attempts").asInt());
        if (node.hasNonNull("errorMessage")) resp.setErrorMessage(node.get("errorMessage").asText());
        if (node.hasNonNull("createdAt")) resp.setCreatedAt(node.get("createdAt").asText());
        if (node.hasNonNull("lastUpdatedAt")) resp.setLastUpdatedAt(node.get("lastUpdatedAt").asText());
        return resp;
    }

    @Data
    static class CreateImportTaskRequest {
        @Schema(description = "Job technical id that created this task", required = true)
        private String jobTechnicalId;
        @Schema(description = "Hacker News id parsed from payload, if available")
        private Long hnItemId;
        @Schema(description = "Initial attempts count (optional)")
        private Integer attempts;
        @Schema(description = "Optional error message")
        private String errorMessage;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created entity")
        private String technicalId;
    }

    @Data
    static class ImportTaskResponse {
        @Schema(description = "Technical id of the task")
        private String technicalId;
        @Schema(description = "Job technical id")
        private String jobTechnicalId;
        @Schema(description = "Hacker News id parsed from payload")
        private Long hnItemId;
        @Schema(description = "Task status (QUEUED, PROCESSING, SUCCEEDED, FAILED)")
        private String status;
        @Schema(description = "Number of attempts")
        private Integer attempts;
        @Schema(description = "Error message if any")
        private String errorMessage;
        @Schema(description = "Created at timestamp")
        private String createdAt;
        @Schema(description = "Last updated at timestamp")
        private String lastUpdatedAt;
    }
}
