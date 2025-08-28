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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "PetImportJob Controller", description = "Controller proxy for PetImportJob entity operations (version 1)")
public class PetImportJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetImportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetImportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Pet Import Job", description = "Creates a PetImportJob entity which triggers the import workflow. Returns only the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreatePetImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Pet import job request",
            required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CreatePetImportJobRequest.class),
                    examples = @ExampleObject(value = "{\"sourceUrl\":\"https://petstore.example/api/pets\",\"requestedAt\":\"2025-08-28T12:00:00Z\"}")
            )
    )
    @PostMapping(path = "/import-pets", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createImportJob(@RequestBody CreatePetImportJobRequest request) {
        try {
            // Basic request validation
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }
            if (request.getRequestedAt() == null || request.getRequestedAt().isBlank()) {
                throw new IllegalArgumentException("requestedAt is required");
            }

            // Build entity - controller remains a thin proxy; minimal initialization to allow persistence/workflow start
            PetImportJob job = new PetImportJob();
            job.setSourceUrl(request.getSourceUrl());
            job.setRequestedAt(request.getRequestedAt());
            // Set initial required fields
            job.setRequestId(UUID.randomUUID().toString());
            job.setStatus("PENDING");
            job.setImportedCount(0);
            job.setErrors(null);

            // Persist via EntityService
            java.util.concurrent.CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    PetImportJob.ENTITY_NAME,
                    PetImportJob.ENTITY_VERSION,
                    job
            );
            UUID technicalId = idFuture.get();

            CreatePetImportJobResponse resp = new CreatePetImportJobResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating PetImportJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating PetImportJob", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating PetImportJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Get Pet Import Job by technicalId", description = "Retrieves a PetImportJob entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getImportJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            java.util.concurrent.CompletableFuture<DataPayload> future = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = future.get();
            if (dataPayload == null) {
                return ResponseEntity.status(404).body("Entity not found");
            }
            JsonNode dataNode = dataPayload.getData();
            if (dataNode == null || dataNode.isNull()) {
                return ResponseEntity.status(404).body("Entity data not found");
            }
            PetImportJobResponse response = objectMapper.treeToValue(dataNode, PetImportJobResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for fetching PetImportJob: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching PetImportJob", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error while fetching PetImportJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // Static DTOs for request/response payloads
    @Data
    @Schema(name = "CreatePetImportJobRequest", description = "Request to create a PetImportJob")
    public static class CreatePetImportJobRequest {
        @Schema(description = "Source URL for importing pets", example = "https://petstore.example/api/pets", required = true)
        private String sourceUrl;

        @Schema(description = "Timestamp when the import was requested", example = "2025-08-28T12:00:00Z", required = true)
        private String requestedAt;
    }

    @Data
    @Schema(name = "CreatePetImportJobResponse", description = "Response containing the technicalId of created PetImportJob")
    public static class CreatePetImportJobResponse {
        @Schema(description = "Technical ID of the created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "PetImportJobResponse", description = "PetImportJob entity representation")
    public static class PetImportJobResponse {
        @Schema(description = "Business request id", example = "req-987")
        private String requestId;

        @Schema(description = "Source URL for importing pets", example = "https://petstore.example/api/pets")
        private String sourceUrl;

        @Schema(description = "Timestamp when the import was requested", example = "2025-08-28T12:00:00Z")
        private String requestedAt;

        @Schema(description = "Status of the job", example = "COMPLETED")
        private String status;

        @Schema(description = "Number of imported pets", example = "42")
        private Integer importedCount;

        @Schema(description = "Errors summary or link", example = "")
        private String errors;
    }
}