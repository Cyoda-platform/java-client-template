package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/ingest-jobs")
@Tag(name = "IngestJob Controller", description = "Proxy controller for IngestJob entity")
public class IngestJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestJobController.class);

    private final EntityService entityService;

    public IngestJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create IngestJob", description = "Create an IngestJob to start splitting and enqueuing items")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IngestJobCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<IngestJobCreateResponse> createIngestJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "IngestJob create request") @RequestBody IngestJobCreateRequest request) {
        try {
            if (request == null || request.getSource() == null || request.getSource().isBlank()) {
                throw new IllegalArgumentException("source is required");
            }

            IngestJob job = new IngestJob();
            job.setSource(request.getSource());
            job.setPayload(request.getPayload());
            job.setStatus("CREATED");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    job
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new IngestJobCreateResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create IngestJob", iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while creating IngestJob", ee);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating IngestJob", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get IngestJob by technicalId", description = "Retrieve an IngestJob by its technical id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getIngestJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get IngestJob", iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while retrieving IngestJob", ee);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving IngestJob", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    public static class IngestJobCreateRequest {
        @Schema(description = "Origin of the job e.g. manual, api", required = true)
        private String source;

        @Schema(description = "Optional bulk JSON payload as string")
        private String payload;
    }

    @Data
    public static class IngestJobCreateResponse {
        @Schema(description = "Technical id of the created IngestJob")
        private String technicalId;

        public IngestJobCreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
