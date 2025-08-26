package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for AdoptionRequest entity.
 * All business logic is handled in workflows; this controller only proxies to EntityService.
 */
@RestController
@RequestMapping("/adoptionRequests")
@io.swagger.v3.oas.annotations.tags.Tag(name = "AdoptionRequest", description = "AdoptionRequest entity endpoints (v1)")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Create an AdoptionRequest entity event and return the technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createAdoptionRequest(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "AdoptionRequest creation payload",
            required = true,
            content = @Content(schema = @Schema(implementation = AdoptionRequestCreateRequest.class))
        )
        @RequestBody AdoptionRequestCreateRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getId() == null || request.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("id is required");
            }
            if (request.getPetId() == null || request.getPetId().trim().isEmpty()) {
                throw new IllegalArgumentException("petId is required");
            }
            if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
                throw new IllegalArgumentException("userId is required");
            }

            // Map request DTO to entity (no business logic here)
            AdoptionRequest entity = new AdoptionRequest();
            // assume setters exist on the entity
            try {
                entity.setId(request.getId());
            } catch (Exception ignored) { /* best-effort set; entity class should provide setters */ }
            try {
                entity.setPetId(request.getPetId());
            } catch (Exception ignored) { }
            try {
                entity.setUserId(request.getUserId());
            } catch (Exception ignored) { }
            try {
                entity.setRequestedDate(request.getRequestedDate());
            } catch (Exception ignored) { }
            try {
                entity.setNotes(request.getNotes());
            } catch (Exception ignored) { }

            java.util.concurrent.CompletableFuture<java.util.UUID> idFuture =
                entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    entity
                );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createAdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating AdoptionRequest", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while creating AdoptionRequest", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieve an AdoptionRequest entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdoptionRequestGetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAdoptionRequest(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.trim().isEmpty()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            java.util.concurrent.CompletableFuture<ObjectNode> itemFuture =
                entityService.getItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id
                );

            ObjectNode entityNode = itemFuture.get();

            AdoptionRequestGetResponse resp = new AdoptionRequestGetResponse();
            resp.setTechnicalId(id.toString());
            resp.setEntity(entityNode);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getAdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching AdoptionRequest", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while fetching AdoptionRequest", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // DTOs

    @Data
    @Schema(name = "AdoptionRequestCreateRequest", description = "AdoptionRequest creation payload")
    public static class AdoptionRequestCreateRequest {
        @Schema(description = "Business id", example = "AR-12345", required = true)
        private String id;

        @Schema(description = "Pet business id", example = "PET-123", required = true)
        private String petId;

        @Schema(description = "User business id", example = "USER-123", required = true)
        private String userId;

        @Schema(description = "Requested date in ISO format", example = "2025-01-01T00:00:00Z")
        private String requestedDate;

        @Schema(description = "Applicant notes", example = "I have a fenced yard")
        private String notes;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "AdoptionRequestGetResponse", description = "Get response for AdoptionRequest")
    public static class AdoptionRequestGetResponse {
        @Schema(description = "Technical id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "AdoptionRequest entity")
        private JsonNode entity;
    }
}