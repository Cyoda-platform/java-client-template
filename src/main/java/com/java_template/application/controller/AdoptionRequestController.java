package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/adoptionRequests")
@Tag(name = "AdoptionRequest Controller", description = "Proxy controller for AdoptionRequest entity (dull proxy)")
public class AdoptionRequestController {
    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);
    private final EntityService entityService;

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Creates an AdoptionRequest entity and triggers its workflow. Returns technicalId only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createAdoptionRequest(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest creation payload") @RequestBody AdoptionRequestRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            AdoptionRequest ar = new AdoptionRequest();
            ar.setId(request.getId());
            ar.setPetId(request.getPetId());
            ar.setOwnerId(request.getOwnerId());
            ar.setRequestDate(request.getRequestDate());
            ar.setNotes(request.getNotes());
            ar.setStatus(request.getStatus() != null ? request.getStatus() : "PENDING");

            if (!ar.isValid()) throw new IllegalArgumentException("Invalid adoption request payload");

            CompletableFuture<UUID> idFuture = entityService.addItem(AdoptionRequest.ENTITY_NAME, String.valueOf(AdoptionRequest.ENTITY_VERSION), ar);
            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Validation error while creating adoption request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException while creating adoption request", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating adoption request", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating adoption request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieves a persisted AdoptionRequest by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getAdoptionRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the adoption request") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(AdoptionRequest.ENTITY_NAME, String.valueOf(AdoptionRequest.ENTITY_VERSION), uuid);
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request in getAdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException in getAdoptionRequest", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getAdoptionRequest", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class AdoptionRequestRequest {
        @Schema(description = "Business id (optional)")
        private String id;
        @Schema(description = "Pet business id / technical id", required = true)
        private String petId;
        @Schema(description = "Owner business id / technical id", required = true)
        private String ownerId;
        @Schema(description = "Request date (ISO datetime)")
        private String requestDate;
        @Schema(description = "Status (optional)")
        private String status;
        @Schema(description = "Notes")
        private String notes;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical identifier assigned to the created entity")
        private String technicalId;
    }
}
