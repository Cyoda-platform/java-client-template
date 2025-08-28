package com.java_template.application.controller.petimportjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

@RestController
@RequestMapping("/jobs/pet-import")
@Tag(name = "PetImportJob", description = "Endpoints for PetImportJob entity (version 1)")
public class PetImportJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetImportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetImportJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create PetImportJob", description = "Create a new PetImportJob. Returns technicalId for the created entity.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPetImportJob(
            @RequestBody(description = "PetImportJob creation request", required = true,
                    content = @Content(schema = @Schema(implementation = CreatePetImportJobRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreatePetImportJobRequest request) {
        try {
            if (request == null || request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }

            // Minimal entity population; workflows will implement business logic.
            PetImportJob entity = new PetImportJob();
            // Set a generated business jobId to satisfy entity validations if needed
            entity.setJobId(UUID.randomUUID().toString());
            entity.setSourceUrl(request.getSourceUrl());
            entity.setRequestedAt(Instant.now().toString());
            entity.setStatus("PENDING");
            entity.setFetchedCount(0);
            entity.setCreatedCount(0);
            entity.setError(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    PetImportJob.ENTITY_NAME,
                    PetImportJob.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();

            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create PetImportJob: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating PetImportJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating PetImportJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error when creating PetImportJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get PetImportJob by technicalId", description = "Retrieve a PetImportJob by its technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetImportJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetImportJob not found");
            }

            JsonNode dataNode = dataPayload.getData();
            if (dataNode == null || dataNode.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetImportJob data not found");
            }

            PetImportJobResponse response = objectMapper.treeToValue(dataNode, PetImportJobResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to get PetImportJob: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving PetImportJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving PetImportJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error when retrieving PetImportJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTO classes for request/response payloads

    @Data
    @Schema(name = "CreatePetImportJobRequest", description = "Request to create a PetImportJob")
    public static class CreatePetImportJobRequest {
        @Schema(description = "Petstore API base URL to import from", example = "https://petstore.example/api/v1/pets", required = true)
        private String sourceUrl;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Creation response returning technical id")
    public static class CreateResponse {
        @Schema(description = "Technical ID of the created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "PetImportJobResponse", description = "PetImportJob representation returned by GET")
    public static class PetImportJobResponse {
        @Schema(description = "Business job id", example = "job-001")
        private String jobId;

        @Schema(description = "Source URL", example = "https://petstore.example/api/v1/pets")
        private String sourceUrl;

        @Schema(description = "ISO timestamp when requested", example = "2025-08-28T12:00:00Z")
        private String requestedAt;

        @Schema(description = "Job status", example = "FETCHING")
        private String status;

        @Schema(description = "Number of pets fetched", example = "12")
        private Integer fetchedCount;

        @Schema(description = "Number of Pet entities created", example = "10")
        private Integer createdCount;

        @Schema(description = "Error details if failed", example = "Timeout while fetching")
        private String error;
    }
}