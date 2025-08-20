package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.ingestionrun.version_1.IngestionRun;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.Data;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/ingestionRuns")
@Tag(name = "IngestionRun")
public class IngestionRunController {
    private final EntityService entityService;
    private static final Logger logger = LoggerFactory.getLogger(IngestionRunController.class);

    public IngestionRunController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create IngestionRun", description = "Create an IngestionRun orchestration record and trigger ingestion workflow. Idempotent on runId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createIngestionRun(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "IngestionRun create request")
            @RequestBody CreateIngestionRunRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getRunId() == null || request.getRunId().isBlank()) {
                throw new IllegalArgumentException("runId is required");
            }
            if (request.getScheduledAt() == null || request.getScheduledAt().isBlank()) {
                throw new IllegalArgumentException("scheduledAt is required");
            }

            IngestionRun run = new IngestionRun();
            run.setRunId(request.getRunId());
            run.setScheduledAt(request.getScheduledAt());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    IngestionRun.ENTITY_NAME,
                    String.valueOf(IngestionRun.ENTITY_VERSION),
                    run
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception creating ingestion run", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error creating ingestion run", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get IngestionRun", description = "Retrieve stored IngestionRun by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getIngestionRun(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestionRun.ENTITY_NAME,
                    String.valueOf(IngestionRun.ENTITY_VERSION),
                    id
            );

            ObjectNode item = itemFuture.get();
            if (item == null) {
                throw new NoSuchElementException("IngestionRun not found: " + technicalId);
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception getting ingestion run", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error getting ingestion run", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Data
    public static class CreateIngestionRunRequest {
        @Schema(description = "user-supplied run id for idempotency", required = true)
        private String runId;

        @Schema(description = "scheduled start time (ISO 8601 UTC)", required = true)
        private String scheduledAt;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "technicalId (UUID)")
        private String technicalId;
    }
}
