package com.java_template.application.controller;

import com.java_template.application.entity.party.version_1.Party;
import com.java_template.application.interactor.PartyInteractor;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.util.CyodaExceptionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: This controller provides REST endpoints for party management including
 * CRUD operations and basic search functionality.
 * All business logic is delegated to PartyInteractor.
 */
@RestController
@RequestMapping("/api/v1/party")
@CrossOrigin(origins = "*")
@Tag(name = "Party Management", description = "APIs for managing parties (borrowers and corporate customers) used as reference data")
public class PartyController {

    private static final Logger logger = LoggerFactory.getLogger(PartyController.class);
    private final PartyInteractor partyInteractor;

    public PartyController(PartyInteractor partyInteractor) {
        this.partyInteractor = partyInteractor;
    }

    @Operation(
        summary = "Create a new party",
        description = "Creates a new party entity. Validates that the partyId is unique before creation."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Party created successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "409", description = "Party with the same partyId already exists",
            content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Invalid party data or creation failed")
    })
    @PostMapping
    public ResponseEntity<?> createParty(
        @Parameter(description = "Party entity to create", required = true)
        @RequestBody Party party) {
        try {
            EntityWithMetadata<Party> response = partyInteractor.createParty(party);
            return ResponseEntity.status(201).body(response);
        } catch (PartyInteractor.DuplicateEntityException e) {
            logger.warn("Duplicate party creation attempt: {}", e.getMessage());
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating party", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(
        summary = "Get party by technical ID",
        description = "Retrieves a party entity by its technical UUID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Party found",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Party not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyById(
        @Parameter(description = "Technical UUID of the party", required = true)
        @PathVariable UUID id) {
        try {
            EntityWithMetadata<Party> response = partyInteractor.getPartyById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting party by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Get party by business ID",
        description = "Retrieves a party entity by its business identifier (partyId)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Party found",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Party not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @GetMapping("/business/{partyId}")
    public ResponseEntity<?> getPartyByBusinessId(
        @Parameter(description = "Business identifier of the party", required = true)
        @PathVariable String partyId) {
        try {
            EntityWithMetadata<Party> response = partyInteractor.getPartyByBusinessId(partyId);
            return ResponseEntity.ok(response);
        } catch (PartyInteractor.EntityNotFoundException e) {
            logger.warn("Party not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting party by business ID: {}", partyId, e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(
        summary = "Update party by business ID",
        description = "Updates a party entity by its business identifier with optional workflow transition"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Party updated successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Party not found",
            content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PutMapping("/business/{partyId}")
    public ResponseEntity<?> updatePartyByBusinessId(
            @Parameter(description = "Business identifier of the party", required = true)
            @PathVariable String partyId,
            @Parameter(description = "Updated party entity", required = true)
            @RequestBody Party party,
            @Parameter(description = "Optional workflow transition name")
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Party> response = partyInteractor.updatePartyByBusinessId(partyId, party, transition);
            return ResponseEntity.ok(response);
        } catch (PartyInteractor.EntityNotFoundException e) {
            logger.warn("Party not found for update: {}", e.getMessage());
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating party by business ID: {}", partyId, e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(
        summary = "Update party by technical ID",
        description = "Updates a party entity by its technical UUID with optional workflow transition"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Party updated successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "400", description = "Invalid party data or update failed")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> updateParty(
            @Parameter(description = "Technical UUID of the party", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Updated party entity", required = true)
            @RequestBody Party party,
            @Parameter(description = "Optional workflow transition name")
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Party> response = partyInteractor.updatePartyById(id, party, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating party", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(
        summary = "Delete party",
        description = "Deletes a party entity by its technical UUID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Party deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Delete operation failed")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteParty(
        @Parameter(description = "Technical UUID of the party", required = true)
        @PathVariable UUID id) {
        try {
            partyInteractor.deleteParty(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting party", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(
        summary = "Get all parties",
        description = "Retrieves all party entities. Use sparingly as this can be slow for large datasets."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parties retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Retrieval failed")
    })
    @GetMapping
    public ResponseEntity<?> getAllParties() {
        try {
            List<EntityWithMetadata<Party>> parties = partyInteractor.getAllParties();
            return ResponseEntity.ok(parties);
        } catch (Exception e) {
            logger.error("Error getting all parties", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }
}
