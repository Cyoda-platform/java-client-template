package com.java_template.application.controller;

import com.java_template.application.entity.marketdata.version_1.MarketData;
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
 * MarketDataController - REST API for market data management
 * Handles market data ingestion, retrieval, and subscriptions
 */
@RestController
@RequestMapping("/ui/marketdata")
@CrossOrigin(origins = "*")
public class MarketDataController {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MarketDataController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create or update market data tick
     * POST /ui/marketdata
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<MarketData>> createMarketData(@Valid @RequestBody MarketData marketData) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(MarketData.ENTITY_NAME).withVersion(MarketData.ENTITY_VERSION);
            EntityWithMetadata<MarketData> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, marketData.getMarketDataId(), "marketDataId", MarketData.class);

            if (existing != null) {
                logger.warn("MarketData with ID {} already exists", marketData.getMarketDataId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("MarketData already exists with ID: %s", marketData.getMarketDataId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<MarketData> response = entityService.create(marketData);
            logger.info("MarketData created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create market data: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get market data by technical UUID
     * GET /ui/marketdata/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<MarketData>> getMarketDataById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MarketData.ENTITY_NAME).withVersion(MarketData.ENTITY_VERSION);
            EntityWithMetadata<MarketData> response = entityService.getById(id, modelSpec, MarketData.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve market data with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update market data
     * PUT /ui/marketdata/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<MarketData>> updateMarketData(
            @PathVariable UUID id,
            @Valid @RequestBody MarketData marketData,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<MarketData> response = entityService.update(id, marketData, transition);
            logger.info("MarketData updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update market data with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get latest market data for a symbol
     * GET /ui/marketdata/symbol/{symbol}
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<EntityWithMetadata<MarketData>> getLatestBySymbol(@PathVariable String symbol) {
        try {
            logger.info("Retrieving latest market data for symbol: {}", symbol);
            // In a real system, this would query for the latest tick
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve market data for symbol '%s': %s", symbol, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

