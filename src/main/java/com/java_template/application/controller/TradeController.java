package com.java_template.application.controller;

import com.java_template.application.entity.trade.version_1.Trade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * TradeController - REST API for trade management
 * Handles trade creation, retrieval, and settlement workflow
 */
@RestController
@RequestMapping("/ui/trade")
@CrossOrigin(origins = "*")
public class TradeController {

    private static final Logger logger = LoggerFactory.getLogger(TradeController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TradeController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new trade
     * POST /ui/trade
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Trade>> createTrade(@Valid @RequestBody Trade trade) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);
            EntityWithMetadata<Trade> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, trade.getTradeId(), "tradeId", Trade.class);

            if (existing != null) {
                logger.warn("Trade with ID {} already exists", trade.getTradeId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Trade already exists with ID: %s", trade.getTradeId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Trade> response = entityService.create(trade);
            logger.info("Trade created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create trade: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get trade by technical UUID
     * GET /ui/trade/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Trade>> getTradeById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);
            EntityWithMetadata<Trade> response = entityService.getById(id, modelSpec, Trade.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve trade with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update trade with optional workflow transition
     * PUT /ui/trade/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Trade>> updateTrade(
            @PathVariable UUID id,
            @Valid @RequestBody Trade trade,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Trade> response = entityService.update(id, trade, transition);
            logger.info("Trade updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update trade with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Settle trade
     * POST /ui/trade/{id}/settle
     */
    @PostMapping("/{id}/settle")
    public ResponseEntity<EntityWithMetadata<Trade>> settleTrade(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);
            EntityWithMetadata<Trade> current = entityService.getById(id, modelSpec, Trade.class);
            EntityWithMetadata<Trade> response = entityService.update(id, current.entity(), "SETTLE");
            logger.info("Trade settled with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to settle trade with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

