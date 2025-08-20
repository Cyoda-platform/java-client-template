package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/ingestion-jobs")
@Tag(name = "IngestionJob API", description = "API for IngestionJob entity (proxy to entity service)")
public class IngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobController.class);
    private final EntityService entityService;

    public IngestionJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create IngestionJob", description = "Create a new ingestion job. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createIngestionJob(@RequestBody(description = "IngestionJob request payload") @org.springframework.web.bind.annotation.RequestBody IngestionJobRequest request) {
        try {
            if (request == null || request.jobName == null || request.jobName.isBlank()) {
                throw new IllegalArgumentException("jobName is required");
            }

            IngestionJob job = new IngestionJob();
            job.setJobName(request.jobName);
            job.setScheduledFor(request.scheduledFor);
            job.setInitiatedBy(request.initiatedBy);
            job.setRunParameters(request.runParameters);
            job.setStatus(request.status != null ? request.status : "PENDING");
            // createdAt is set by downstream processors/stores

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    job
            );

            UUID id = idFuture.get();
            URI location = URI.create(String.format("/ingestion-jobs/%s", id.toString()));
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(location);
            return ResponseEntity.created(location).body(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error creating ingestion job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ingestion job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating ingestion job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get IngestionJob by technicalId", description = "Retrieve an ingestion job by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    id
            );

            ObjectNode obj = itemFuture.get();
            return ResponseEntity.ok(obj);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error retrieving ingestion job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving ingestion job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error retrieving ingestion job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List IngestionJobs", description = "List all ingestion jobs")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listIngestionJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error listing ingestion jobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing ingestion jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error listing ingestion jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search IngestionJobs", description = "Search ingestion jobs by a simple field condition (field, operator, value)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchIngestionJobs(
            @RequestParam(required = true) String field,
            @RequestParam(required = true) String operator,
            @RequestParam(required = true) String value
    ) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + field, operator, value)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error searching ingestion jobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching ingestion jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error searching ingestion jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class IngestionJobRequest {
        @Schema(description = "Human readable job name", required = true)
        public String jobName;
        @Schema(description = "Scheduled start time (ISO-8601)")
        public String scheduledFor;
        @Schema(description = "Initiated by (user or scheduler)")
        public String initiatedBy;
        @Schema(description = "Run parameters")
        public java.util.Map<String, Object> runParameters;
        @Schema(description = "Initial status (optional)")
        public String status;
    }

    @Data
    static class IdResponse {
        @Schema(description = "Technical id assigned to the entity")
        public String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
