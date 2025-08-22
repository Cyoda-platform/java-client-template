package com.java_template.application.controller.petenrichmentjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for PetEnrichmentJob entity. All business logic lives in workflows.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "PetEnrichmentJob", description = "Controller proxy for PetEnrichmentJob entity")
public class PetEnrichmentJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichmentJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetEnrichmentJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create PetEnrichmentJob", description = "Creates a PetEnrichmentJob which starts the enrichment workflow.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = CreatePetEnrichmentJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/enrich-pets", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createPetEnrichmentJob(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "PetEnrichmentJob creation request",
            required = true,
            content = @Content(schema = @Schema(implementation = CreatePetEnrichmentJobRequest.class))
        )
        @Valid @RequestBody CreatePetEnrichmentJobRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getPetSource() == null || request.getPetSource().trim().isEmpty()) {
                throw new IllegalArgumentException("petSource is required");
            }

            // Map request to entity. Controller must not implement business logic.
            PetEnrichmentJob job = new PetEnrichmentJob();
            // attempt to set fields if available on the entity; if not, EntityService may accept a partial POJO.
            try {
                job.setPetSource(request.getPetSource());
            } catch (NoSuchMethodError | NoSuchMethodException | AbstractMethodError ignored) {
                logger.debug("PetEnrichmentJob.setPetSource not present or inaccessible; continuing with raw mapping.");
            } catch (Throwable t) {
                // fallback: ignore and let EntityService handle raw object mapping
                logger.debug("Ignored error while setting petSource on PetEnrichmentJob: {}", t.getMessage());
            }
            try {
                job.setRequestedBy(request.getRequestedBy());
            } catch (Throwable ignored) {
                logger.debug("Ignored error while setting requestedBy on PetEnrichmentJob");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                PetEnrichmentJob.ENTITY_NAME,
                String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                job
            );

            UUID technicalId = idFuture.get();

            CreatePetEnrichmentJobResponse resp = new CreatePetEnrichmentJobResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating PetEnrichmentJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating PetEnrichmentJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating PetEnrichmentJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating PetEnrichmentJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get PetEnrichmentJob by technicalId", description = "Retrieves a PetEnrichmentJob by its technical UUID.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = PetEnrichmentJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getPetEnrichmentJob(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.trim().isEmpty()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                PetEnrichmentJob.ENTITY_NAME,
                String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                uuid
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEnrichmentJob not found");
            }

            // Map to response DTO
            PetEnrichmentJobResponse resp;
            try {
                resp = objectMapper.treeToValue(node, PetEnrichmentJobResponse.class);
            } catch (Exception ex) {
                // If mapping fails, return raw JSON node
                logger.debug("Mapping to PetEnrichmentJobResponse failed, returning raw JSON: {}", ex.getMessage());
                return ResponseEntity.ok(node);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for retrieving PetEnrichmentJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving PetEnrichmentJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving PetEnrichmentJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving PetEnrichmentJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTO classes for request/response payloads

    @Data
    @Schema(name = "CreatePetEnrichmentJobRequest", description = "Request to create a PetEnrichmentJob")
    public static class CreatePetEnrichmentJobRequest {
        @Schema(description = "Pet source (e.g. petstore endpoint or catalog id)", example = "petstore/v1/pets?limit=50", required = true)
        private String petSource;

        @Schema(description = "User id who requested the job", example = "user-123", required = false)
        private String requestedBy;
    }

    @Data
    @Schema(name = "CreatePetEnrichmentJobResponse", description = "Response containing the technical id of the created job")
    public static class CreatePetEnrichmentJobResponse {
        @Schema(description = "Technical ID of the created job", example = "job-tech-789")
        private String technicalId;
    }

    @Data
    @Schema(name = "PetEnrichmentJobResponse", description = "PetEnrichmentJob entity representation")
    public static class PetEnrichmentJobResponse {
        @Schema(description = "Business job id", example = "job-123")
        private String jobId;

        @Schema(description = "Pet source (endpoint or catalog id)", example = "petstore/v1/pets?limit=50")
        private String petSource;

        @Schema(description = "Job status", example = "COMPLETED")
        private String status;

        @Schema(description = "Number of pets fetched", example = "48")
        private Integer fetchedCount;

        @Schema(description = "List of errors encountered during processing")
        private List<String> errors;

        @Schema(description = "ISO created at timestamp", example = "2025-08-01T12:00:00Z")
        private String createdAt;
    }
}