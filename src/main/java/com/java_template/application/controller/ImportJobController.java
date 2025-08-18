package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/importJobs")
@Tag(name = "ImportJob API", description = "Endpoints for managing import jobs")
public class ImportJobController {
    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ImportJob", description = "Create a new import job and trigger import processors. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createImportJob(@RequestBody CreateImportJobRequest request) {
        try {
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }
            if (request.getRequestedBy() == null || request.getRequestedBy().isBlank()) {
                throw new IllegalArgumentException("requestedBy is required");
            }

            ImportJob job = new ImportJob();
            job.setJobId(request.getJobId());
            job.setSourceUrl(request.getSourceUrl());
            job.setRequestedBy(request.getRequestedBy());
            job.setStatus(request.getStatus());
            job.setImportedCount(request.getImportedCount());
            job.setErrorMessage(request.getErrorMessage());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    job
            );
            UUID technicalId = idFuture.get();

            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create import job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", cause.getMessage()));
            } else {
                logger.error("Execution exception while creating import job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating import job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while creating import job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "Get ImportJob", description = "Retrieve an import job by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getImportJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            ImportJobResponse resp = objectMapper.treeToValue(node, ImportJobResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get import job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", cause.getMessage()));
            } else {
                logger.error("Execution exception while retrieving import job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving import job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving import job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    // DTOs
    @Data
    static class CreateImportJobRequest {
        @Schema(description = "Business job id (optional)")
        private String jobId;
        @Schema(description = "Source URL", required = true)
        private String sourceUrl;
        @Schema(description = "Requested by (user technicalId)", required = true)
        private String requestedBy;
        @Schema(description = "Status (optional)")
        private String status;
        @Schema(description = "Imported count (optional)")
        private Integer importedCount;
        @Schema(description = "Error message (optional)")
        private String errorMessage;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id assigned to the entity")
        private String technicalId;
    }

    @Data
    static class ImportJobResponse {
        @Schema(description = "Technical id")
        private String technicalId;
        @Schema(description = "Business job id (optional)")
        private String jobId;
        @Schema(description = "Source URL")
        private String sourceUrl;
        @Schema(description = "Requested by (user technicalId)")
        private String requestedBy;
        @Schema(description = "Status")
        private String status;
        @Schema(description = "Imported count")
        private Integer importedCount;
        @Schema(description = "Error message")
        private String errorMessage;
        @Schema(description = "Notification sent flag")
        private Boolean notificationSent;
        @Schema(description = "Created at")
        private String createdAt;
        @Schema(description = "Updated at")
        private String updatedAt;
    }

    @Data
    static class ErrorResponse {
        private final String code;
        private final String message;
    }
}
