package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.owner.version_1.Owner;
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
@RequestMapping("/owners")
@Tag(name = "Owner Controller", description = "Proxy controller for Owner entity (dull proxy)")
public class OwnerController {
    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);
    private final EntityService entityService;

    public OwnerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Owner", description = "Creates an Owner entity and triggers the Owner workflow. Returns technicalId only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createOwner(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner creation payload") @RequestBody OwnerRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Owner owner = new Owner();
            owner.setId(request.getId());
            owner.setName(request.getName());
            owner.setContactEmail(request.getContactEmail());
            owner.setContactPhone(request.getContactPhone());
            owner.setAddress(request.getAddress());
            owner.setRole(request.getRole());
            if (request.getFavorites() != null) owner.setFavorites(request.getFavorites());
            if (request.getAdoptionHistory() != null) owner.setAdoptionHistory(request.getAdoptionHistory());

            if (!owner.isValid()) throw new IllegalArgumentException("Invalid owner payload");

            CompletableFuture<UUID> idFuture = entityService.addItem(Owner.ENTITY_NAME, String.valueOf(Owner.ENTITY_VERSION), owner);
            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Validation error while creating owner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException while creating owner", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Owner by technicalId", description = "Retrieves a persisted Owner by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the owner") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Owner.ENTITY_NAME, String.valueOf(Owner.ENTITY_VERSION), uuid);
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request in getOwner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException in getOwner", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getOwner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getOwner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class OwnerRequest {
        @Schema(description = "Business id (optional)")
        private String id;
        @Schema(description = "Full name", required = true)
        private String name;
        @Schema(description = "Contact email", required = true)
        private String contactEmail;
        @Schema(description = "Contact phone")
        private String contactPhone;
        @Schema(description = "Postal address")
        private String address;
        @Schema(description = "Role (CUSTOMER/ADMIN/STAFF)")
        private String role;
        @Schema(description = "Favorites list")
        private java.util.List<String> favorites;
        @Schema(description = "Adoption history list")
        private java.util.List<String> adoptionHistory;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical identifier assigned to the created entity")
        private String technicalId;
    }
}
