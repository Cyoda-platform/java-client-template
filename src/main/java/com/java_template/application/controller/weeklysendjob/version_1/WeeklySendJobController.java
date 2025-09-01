package com.java_template.application.controller.weeklysendjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

/**
 * Dull proxy controller for WeeklySendJob entity.
 * All business logic is handled in workflows; controller only proxies requests to EntityService.
 */
@RestController
@RequestMapping("/weekly-send-jobs")
@Tag(name = "WeeklySendJob", description = "API for WeeklySendJob entity (version 1)")
@RequiredArgsConstructor
public class WeeklySendJobController {

    private static final Logger logger = LoggerFactory.getLogger(WeeklySendJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Create WeeklySendJob", description = "Create a WeeklySendJob. Returns technicalId of the created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = CreateWeeklySendJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createWeeklySendJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "WeeklySendJob create request",
                    content = @Content(schema = @Schema(implementation = CreateWeeklySendJobRequest.class)))
            @RequestBody CreateWeeklySendJobRequest request) {
        try {
            if (request == null || request.getScheduledFor() == null || request.getScheduledFor().isBlank()) {
                throw new IllegalArgumentException("scheduledFor is required");
            }

            WeeklySendJob job = new WeeklySendJob();
            // Minimal setters to persist; workflows will adjust real values.
            job.setScheduledFor(request.getScheduledFor());
            job.setCreatedAt(Instant.now().toString());
            // set runAt and catFactTechnicalId with placeholders to satisfy entity constraints if necessary
            job.setRunAt(job.getCreatedAt());
            job.setCatFactTechnicalId(UUID.randomUUID().toString());
            job.setStatus("CREATED");
            job.setErrorMessage(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    WeeklySendJob.ENTITY_NAME,
                    WeeklySendJob.ENTITY_VERSION,
                    job
            );
            UUID createdId = idFuture.get();

            CreateWeeklySendJobResponse resp = new CreateWeeklySendJobResponse();
            resp.setTechnicalId(createdId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating WeeklySendJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating WeeklySendJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating WeeklySendJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating WeeklySendJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get WeeklySendJob by technicalId", description = "Retrieve a WeeklySendJob by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = GetWeeklySendJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getWeeklySendJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("WeeklySendJob not found");
            }

            ObjectNode node = (ObjectNode) dataPayload.getData();
            GetWeeklySendJobResponse response = objectMapper.treeToValue(node, GetWeeklySendJobResponse.class);

            // extract technicalId from metadata if present
            if (dataPayload.getMeta() != null && dataPayload.getMeta().has("entityId")) {
                response.setTechnicalId(dataPayload.getMeta().get("entityId").asText());
            } else {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getWeeklySendJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving WeeklySendJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving WeeklySendJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving WeeklySendJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "CreateWeeklySendJobRequest", description = "Request to create a WeeklySendJob")
    public static class CreateWeeklySendJobRequest {
        @Schema(description = "ISO date/time when the weekly job should run", example = "2025-09-07T09:00:00Z", required = true)
        private String scheduledFor;
    }

    @Data
    @Schema(name = "CreateWeeklySendJobResponse", description = "Response after creating WeeklySendJob")
    public static class CreateWeeklySendJobResponse {
        @Schema(description = "Technical ID of the created WeeklySendJob", example = "job-uuid-5678")
        private String technicalId;
    }

    @Data
    @Schema(name = "GetWeeklySendJobResponse", description = "WeeklySendJob retrieve response")
    public static class GetWeeklySendJobResponse {
        @Schema(description = "Technical ID of the WeeklySendJob", example = "job-uuid-5678")
        private String technicalId;

        @Schema(description = "ISO date/time when the weekly job should run", example = "2025-09-07T09:00:00Z")
        private String scheduledFor;

        @Schema(description = "ISO timestamp when job was created", example = "2025-09-01T10:00:00Z")
        private String createdAt;

        @Schema(description = "ISO timestamp when job actually started", example = "2025-09-07T09:00:05Z")
        private String runAt;

        @Schema(description = "Reference to CatFact technicalId", example = "catfact-uuid-999")
        private String catFactTechnicalId;

        @Schema(description = "Job status", example = "COMPLETED")
        private String status;

        @Schema(description = "Optional error message if job failed", example = "Timeout when calling catfact API")
        private String errorMessage;
    }
}