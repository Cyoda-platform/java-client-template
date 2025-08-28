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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
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
            if (request == null || request.sourceUrl == null || request.sourceUrl.isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }

            // Create entity instance and set fields via reflection to avoid relying on generated setters
            PetImportJob entity = new PetImportJob();
            // Set business jobId, may be used by workflows
            setField(entity, "jobId", UUID.randomUUID().toString());
            setField(entity, "sourceUrl", request.sourceUrl);
            setField(entity, "requestedAt", Instant.now().toString());
            setField(entity, "status", "PENDING");
            setField(entity, "fetchedCount", Integer.valueOf(0));
            setField(entity, "createdCount", Integer.valueOf(0));
            setField(entity, "error", null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    PetImportJob.ENTITY_NAME,
                    PetImportJob.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();

            CreateResponse resp = new CreateResponse();
            resp.technicalId = technicalId.toString();
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

    // Reflection helper to set private fields on entity without relying on setters
    private static void setField(Object target, String fieldName, Object value) {
        if (target == null) return;
        Class<?> cls = target.getClass();
        try {
            Field f = null;
            Class<?> cur = cls;
            // traverse class hierarchy to find field (in case of inheritance)
            while (cur != null) {
                try {
                    f = cur.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException nsf) {
                    cur = cur.getSuperclass();
                }
            }
            if (f == null) {
                // field not found; nothing to set
                return;
            }
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            // Log but do not throw to keep controller responsibilities separated from business logic
            LoggerFactory.getLogger(PetImportJobController.class).warn("Unable to set field '{}' on {}: {}", fieldName, cls.getSimpleName(), e.getMessage());
        }
    }

    // Static DTO classes for request/response payloads

    @Schema(name = "CreatePetImportJobRequest", description = "Request to create a PetImportJob")
    public static class CreatePetImportJobRequest {
        @Schema(description = "Petstore API base URL to import from", example = "https://petstore.example/api/v1/pets", required = true)
        public String sourceUrl;
    }

    @Schema(name = "CreateResponse", description = "Creation response returning technical id")
    public static class CreateResponse {
        @Schema(description = "Technical ID of the created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        public String technicalId;
    }

    @Schema(name = "PetImportJobResponse", description = "PetImportJob representation returned by GET")
    public static class PetImportJobResponse {
        @Schema(description = "Business job id", example = "job-001")
        public String jobId;

        @Schema(description = "Source URL", example = "https://petstore.example/api/v1/pets")
        public String sourceUrl;

        @Schema(description = "ISO timestamp when requested", example = "2025-08-28T12:00:00Z")
        public String requestedAt;

        @Schema(description = "Job status", example = "FETCHING")
        public String status;

        @Schema(description = "Number of pets fetched", example = "12")
        public Integer fetchedCount;

        @Schema(description = "Number of Pet entities created", example = "10")
        public Integer createdCount;

        @Schema(description = "Error details if failed", example = "Timeout while fetching")
        public String error;
    }
}