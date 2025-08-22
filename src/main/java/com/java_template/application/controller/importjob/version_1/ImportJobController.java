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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for ImportJob entity. All business logic lives in workflows.
 */
@RestController
@RequestMapping("/import-jobs")
@Tag(name = "ImportJob", description = "API for ImportJob entity (version 1) - proxy to EntityService")
@RequiredArgsConstructor
public class ImportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);

    private final EntityService entityService;

    @Operation(summary = "Create ImportJob", description = "Creates an ImportJob entity and starts the import workflow. Returns only the technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createImportJob(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Import job creation request", required = true,
            content = @Content(schema = @Schema(implementation = CreateImportJobRequest.class)))
        @RequestBody CreateImportJobRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getPayload() == null) {
                throw new IllegalArgumentException("payload is required");
            }
            // Build entity instance and proxy to entityService
            ImportJob importJob = new ImportJob();
            importJob.setPayload(request.getPayload());
            importJob.setCreatedBy(request.getCreatedBy());
            // Do not set status/result/error here; workflows handle that.

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ImportJob.ENTITY_NAME,
                String.valueOf(ImportJob.ENTITY_VERSION),
                importJob
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create ImportJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception when creating ImportJob", e);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating ImportJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error when creating ImportJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Get ImportJob by technicalId", description = "Retrieves an ImportJob entity by its technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getImportJobById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
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
            if (item == null || item.isNull()) {
                return ResponseEntity.status(404).body("ImportJob not found");
            }

            ImportJobResponse resp = new ImportJobResponse();
            resp.setTechnicalId(technicalId);

            // payload
            JsonNode payloadNode = item.get("payload");
            resp.setPayload(payloadNode);

            // createdBy
            JsonNode createdByNode = item.get("createdBy");
            resp.setCreatedBy(createdByNode != null && !createdByNode.isNull() ? createdByNode.asText() : null);

            // status
            JsonNode statusNode = item.get("status");
            resp.setStatus(statusNode != null && !statusNode.isNull() ? statusNode.asText() : null);

            // resultItemId
            JsonNode resultItemIdNode = item.get("resultItemId");
            resp.setResultItemId(resultItemIdNode != null && !resultItemIdNode.isNull() ? resultItemIdNode.asInt() : null);

            // errorMessage
            JsonNode errorMessageNode = item.get("errorMessage");
            resp.setErrorMessage(errorMessageNode != null && !errorMessageNode.isNull() ? errorMessageNode.asText() : null);

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get ImportJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("ImportJob not found: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument when getting ImportJob: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception when retrieving ImportJob", e);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving ImportJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving ImportJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "CreateImportJobRequest", description = "Request to create an ImportJob")
    public static class CreateImportJobRequest {
        @Schema(description = "Full Hacker News JSON payload to ingest", required = true)
        private JsonNode payload;

        @Schema(description = "Caller id or system creating the job", required = false)
        private String createdBy;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity", example = "job-12345")
        private String technicalId;
    }

    @Data
    @Schema(name = "ImportJobResponse", description = "ImportJob entity representation returned to clients")
    public static class ImportJobResponse {
        @Schema(description = "Technical ID of the entity", example = "job-12345")
        private String technicalId;

        @Schema(description = "Original payload JSON")
        private JsonNode payload;

        @Schema(description = "Caller id or system that created the job")
        private String createdBy;

        @Schema(description = "Job status (PENDING, IN_PROGRESS, COMPLETED, FAILED)")
        private String status;

        @Schema(description = "Result HN item id created when successful")
        private Integer resultItemId;

        @Schema(description = "Error message in case of failure")
        private String errorMessage;
    }
}