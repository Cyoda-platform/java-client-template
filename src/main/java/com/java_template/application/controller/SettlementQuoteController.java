package com.java_template.application.controller;

import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
import com.java_template.application.interactor.SettlementQuoteInteractor;
import com.java_template.common.dto.EntityWithMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * ABOUTME: REST controller for settlement quote management. Delegates all business logic to SettlementQuoteInteractor.
 */
@RestController
@RequestMapping("/api/v1/settlement-quote")
@CrossOrigin(origins = "*")
@Tag(name = "Settlement Quote Management", description = "APIs for managing loan settlement quotes")
public class SettlementQuoteController {
    
    private static final Logger logger = LoggerFactory.getLogger(SettlementQuoteController.class);
    private final SettlementQuoteInteractor settlementQuoteInteractor;

    public SettlementQuoteController(SettlementQuoteInteractor settlementQuoteInteractor) {
        this.settlementQuoteInteractor = settlementQuoteInteractor;
    }

    @Operation(summary = "Create a new settlement quote", description = "Creates a new settlement quote entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Settlement quote created successfully"),
        @ApiResponse(responseCode = "409", description = "Settlement quote with the same ID already exists"),
        @ApiResponse(responseCode = "400", description = "Invalid data or creation failed")
    })
    @PostMapping
    public ResponseEntity<?> createSettlementQuote(
        @Parameter(description = "Settlement quote entity to create", required = true)
        @RequestBody SettlementQuote settlementQuote) {
        try {
            EntityWithMetadata<SettlementQuote> response = settlementQuoteInteractor.createSettlementQuote(settlementQuote);
            return ResponseEntity.status(201).body(response);
        } catch (SettlementQuoteInteractor.DuplicateEntityException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating settlement quote", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get settlement quote by technical ID")
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> getSettlementQuoteById(
        @Parameter(description = "Technical UUID", required = true) @PathVariable UUID id) {
        try {
            EntityWithMetadata<SettlementQuote> response = settlementQuoteInteractor.getSettlementQuoteById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting settlement quote by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get settlement quote by business ID")
    @GetMapping("/business/{settlementQuoteId}")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> getSettlementQuoteByBusinessId(
        @Parameter(description = "Business identifier", required = true) @PathVariable String settlementQuoteId) {
        try {
            EntityWithMetadata<SettlementQuote> response = settlementQuoteInteractor.getSettlementQuoteByBusinessId(settlementQuoteId);
            return ResponseEntity.ok(response);
        } catch (SettlementQuoteInteractor.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting settlement quote by business ID: {}", settlementQuoteId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Update settlement quote by business ID")
    @PutMapping("/business/{settlementQuoteId}")
    public ResponseEntity<?> updateSettlementQuoteByBusinessId(
            @Parameter(description = "Business identifier", required = true) @PathVariable String settlementQuoteId,
            @Parameter(description = "Updated settlement quote", required = true) @RequestBody SettlementQuote settlementQuote,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<SettlementQuote> response = settlementQuoteInteractor.updateSettlementQuoteByBusinessId(settlementQuoteId, settlementQuote, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating settlement quote by business ID: {}", settlementQuoteId, e);
            return ResponseEntity.status(404).body("SettlementQuote with settlementQuoteId '" + settlementQuoteId + "' not found");
        }
    }

    @Operation(summary = "Update settlement quote by technical ID")
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> updateSettlementQuote(
            @Parameter(description = "Technical UUID", required = true) @PathVariable UUID id,
            @Parameter(description = "Updated settlement quote", required = true) @RequestBody SettlementQuote settlementQuote,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<SettlementQuote> response = settlementQuoteInteractor.updateSettlementQuoteById(id, settlementQuote, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating settlement quote", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get all settlement quotes", description = "Retrieves all settlement quote entities")
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<SettlementQuote>>> getAllSettlementQuotes() {
        try {
            List<EntityWithMetadata<SettlementQuote>> quotes = settlementQuoteInteractor.getAllSettlementQuotes();
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            logger.error("Error getting all settlement quotes", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

