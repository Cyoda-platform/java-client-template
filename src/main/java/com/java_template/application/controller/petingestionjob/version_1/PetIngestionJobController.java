package com.java_template.application.controller.petingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Data;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/jobs/pet-ingestion")
@Tag(name = "PetIngestionJob", description = "Controller for PetIngestionJob orchestration entity (version 1)")
public class PetIngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetIngestionJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetIngestionJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Pet Ingestion Job", description = "Create a new PetIngestionJob orchestration entity. Returns only the technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createJob(
            @Parameter(description = "Create PetIngestionJob request payload")
            @RequestBody CreatePetIngestionJobRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (request.getJobName() == null || request.getJobName().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            PetIngestionJob entity = new PetIngestionJob();
            // Basic initialization only (no business logic)
            entity.setJobName(request.getJobName());
            entity.setSourceUrl(request.getSourceUrl());
            entity.setStartedAt(Instant.now().toString());
            entity.setStatus("PENDING");
            entity.setProcessedCount(0);
            entity.setErrors(new ArrayList<>());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                PetIngestionJob.ENTITY_NAME,
                PetIngestionJob.ENTITY_VERSION,
                entity
            );
            UUID entityId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating PetIngestionJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when creating PetIngestionJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating PetIngestionJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error when creating PetIngestionJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get PetIngestionJob by technicalId", description = "Retrieve a PetIngestionJob orchestration entity by its technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetIngestionJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<PetIngestionJobResponse> getJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            JsonNode dataNode = dataPayload.getData();
            PetIngestionJobResponse resp = objectMapper.treeToValue(dataNode, PetIngestionJobResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getJobById: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when retrieving PetIngestionJob {}", technicalId, ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving PetIngestionJob {}", technicalId, ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error when retrieving PetIngestionJob {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    @Schema(name = "CreatePetIngestionJobRequest", description = "Request payload to create a PetIngestionJob")
    public static class CreatePetIngestionJobRequest {
        @Schema(description = "Job name", example = "PetstoreSync", required = true)
        private String jobName;

        @Schema(description = "Source URL to fetch pets from", example = "https://petstore.example/api/pets", required = true)
        private String sourceUrl;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId of the created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity", example = "job_abc123")
        private String technicalId;
    }

    @Data
    @Schema(name = "PetIngestionJobResponse", description = "Response payload for PetIngestionJob")
    public static class PetIngestionJobResponse {
        @Schema(description = "Technical ID of the entity", example = "job_abc123")
        private String technicalId;

        @Schema(description = "Job name", example = "PetstoreSync")
        private String jobName;

        @Schema(description = "Source URL", example = "https://petstore.example/api/pets")
        private String sourceUrl;

        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "ISO-8601 timestamp when job started", example = "2025-08-28T10:00:00Z")
        private String startedAt;

        @Schema(description = "ISO-8601 timestamp when job completed", example = "2025-08-28T10:00:30Z")
        private String completedAt;

        @Schema(description = "Number of processed items", example = "120")
        private Integer processedCount;

        @Schema(description = "List of error messages, if any")
        private List<String> errors = new ArrayList<>();
    }
}