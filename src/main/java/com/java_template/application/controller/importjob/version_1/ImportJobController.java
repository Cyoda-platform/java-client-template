package com.java_template.application.controller.importjob.version_1;

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
import java.time.Instant;
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
}