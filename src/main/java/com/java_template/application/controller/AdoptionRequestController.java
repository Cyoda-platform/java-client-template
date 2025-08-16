package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/adoptionRequests")
@Tag(name = "AdoptionRequest API", description = "APIs to create and fetch AdoptionRequest entities")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);
    private final EntityService entityService;

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @Schema(description = "AdoptionRequest create request")
    public static class AdoptionRequestCreate {
        @Schema(description = "Pet technical id (internal)")
        private String petTechnicalId;
        @Schema(description = "Pet external id (optional)")
        private String petExternalId;
        @Schema(description = "Name of the requester")
        private String requesterName;
        @Schema(description = "Contact info of the requester (email/phone)")
        private String requesterContact;
        @Schema(description = "Optional notes")
        private String notes;
    }

    @Data
    @Schema(description = "Technical id response")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the created entity")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) { this.technicalId = technicalId; }
    }

    @Operation(summary = "Create AdoptionRequest", description = "Creates a new AdoptionRequest with status SUBMITTED. Returns technicalId immediately.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequestCreate request) {
        try {
            if (request == null) throw new IllegalArgumentException("request body is required");
            AdoptionRequest ar = new AdoptionRequest();
            ar.setPetId(request.getPetTechnicalId());
            ar.setRequesterName(request.getRequesterName());
            ar.setRequesterContact(request.getRequesterContact());
            ar.setNotes(request.getNotes());
            ar.setStatus("SUBMITTED");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    ar
            );

            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieves the full persisted AdoptionRequest by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAdoptionRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }
}
