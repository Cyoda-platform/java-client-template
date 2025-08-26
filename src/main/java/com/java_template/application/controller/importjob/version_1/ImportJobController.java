package com.java_template.application.controller.importjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.examples.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/import-jobs")
@Tag(name = "ImportJob", description = "APIs for ImportJob entity (version 1)")
public class ImportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);

    private final EntityService entityService;

    public ImportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ImportJob", description = "Create an ImportJob which triggers downstream processing. Returns only the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createImportJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Import job payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateImportJobRequest.class)))
            @RequestBody CreateImportJobRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getItemJson() == null) {
                throw new IllegalArgumentException("itemJson is required");
            }

            ImportJob job = new ImportJob();
            job.setJobId(request.getJobId());
            job.setItemJson(request.getItemJson());
            job.setCreatedAt(Instant.now().toString());
            // initial status per workflow
            job.setStatus("PENDING");
            // processedItemId intentionally left null until processing completes
            job.setProcessedItemId(null);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();

            CreateImportJobResponse resp = new CreateImportJobResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create ImportJob: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException when creating ImportJob", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ImportJob", description = "Retrieve ImportJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    uuid
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ImportJob not found");
            }

            ImportJobResponse resp = new ImportJobResponse();
            resp.setTechnicalId(technicalId);

            JsonNode jobIdNode = node.get("jobId");
            if (jobIdNode != null && !jobIdNode.isNull()) resp.setJobId(jobIdNode.asText());

            JsonNode createdAtNode = node.get("createdAt");
            if (createdAtNode != null && !createdAtNode.isNull()) resp.setCreatedAt(createdAtNode.asText());

            JsonNode statusNode = node.get("status");
            if (statusNode != null && !statusNode.isNull()) resp.setStatus(statusNode.asText());

            JsonNode processedItemIdNode = node.get("processedItemId");
            if (processedItemIdNode != null && !processedItemIdNode.isNull() && processedItemIdNode.canConvertToLong()) {
                resp.setProcessedItemId(processedItemIdNode.asLong());
            } else {
                resp.setProcessedItemId(null);
            }

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get ImportJob: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException when retrieving ImportJob", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs

    @Data
    @Schema(name = "CreateImportJobRequest", description = "Request payload to create an ImportJob")
    public static class CreateImportJobRequest {
        @Schema(description = "Optional client-provided job identifier", example = "optional-client-id")
        private String jobId;

        @Schema(description = "The Hacker News JSON payload to import", required = true)
        private Object itemJson;
    }

    @Data
    @Schema(name = "CreateImportJobResponse", description = "Response after creating an ImportJob")
    public static class CreateImportJobResponse {
        @Schema(description = "Technical id assigned by the platform", example = "importJob-abc123")
        private String technicalId;
    }

    @Data
    @Schema(name = "ImportJobResponse", description = "ImportJob representation returned by GET")
    public static class ImportJobResponse {
        @Schema(description = "Technical id assigned by the platform", example = "importJob-abc123")
        private String technicalId;

        @Schema(description = "Client provided job id", example = "optional-client-id")
        private String jobId;

        @Schema(description = "ISO-8601 creation timestamp", example = "2025-08-26T12:00:00Z")
        private String createdAt;

        @Schema(description = "Status of the import job", example = "COMPLETED")
        private String status;

        @Schema(description = "Processed HN item id if available", example = "12345")
        private Long processedItemId;
    }
}