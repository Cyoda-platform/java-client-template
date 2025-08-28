package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.Data;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/adoption-requests")
@Tag(name = "AdoptionRequest", description = "AdoptionRequest entity proxy endpoints (version 1)")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionRequestController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Persist a new AdoptionRequest entity and start its workflow. Returns technicalId only.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    @PostMapping
    public ResponseEntity<?> createAdoptionRequest(
            @RequestBody(description = "Adoption request payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateAdoptionRequestRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateAdoptionRequestRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getPetId() == null || request.getPetId().isBlank()) {
                throw new IllegalArgumentException("petId is required");
            }
            if (request.getRequesterId() == null || request.getRequesterId().isBlank()) {
                throw new IllegalArgumentException("requesterId is required");
            }

            AdoptionRequest entity = new AdoptionRequest();
            // Controller should not implement business logic. Map only provided fields.
            entity.setPetId(request.getPetId());
            entity.setRequesterId(request.getRequesterId());
            entity.setNotes(request.getNotes());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    AdoptionRequest.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create AdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating AdoptionRequest", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating AdoptionRequest", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieve the persisted AdoptionRequest and its current state by technicalId")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdoptionRequestResponse.class)))
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "404", description = "Not Found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAdoptionRequestById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
            }
            AdoptionRequest entity = objectMapper.treeToValue(dataPayload.getData(), AdoptionRequest.class);
            AdoptionRequestResponse response = new AdoptionRequestResponse();
            response.setTechnicalId(technicalId);
            response.setEntity(entity);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid get request for AdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving AdoptionRequest", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving AdoptionRequest", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "CreateAdoptionRequestRequest", description = "Request payload to create an AdoptionRequest")
    public static class CreateAdoptionRequestRequest {
        @Schema(description = "Reference to the pet (business or serialized id)", example = "pet-source-123", required = true)
        private String petId;

        @Schema(description = "Reference to the requester/owner (business or serialized id)", example = "owner-source-456", required = true)
        private String requesterId;

        @Schema(description = "Optional notes provided by the requester", example = "I have a fenced yard and previous experience")
        private String notes;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId of the persisted entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the persisted entity", example = "request-technical-1357")
        private String technicalId;
    }

    @Data
    @Schema(name = "AdoptionRequestResponse", description = "Response containing the technicalId and the stored AdoptionRequest entity")
    public static class AdoptionRequestResponse {
        @Schema(description = "Technical ID of the entity", example = "request-technical-1357")
        private String technicalId;

        @Schema(description = "Stored AdoptionRequest entity")
        private AdoptionRequest entity;
    }
}