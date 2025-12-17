package com.java_template.application.controller;

import com.java_template.application.entity.market.version_1.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * MarketController - REST API for Market entity management
 */
@RestController
@RequestMapping("/ui/market")
@CrossOrigin(origins = "*")
public class MarketController {

    private static final Logger logger = LoggerFactory.getLogger(MarketController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MarketController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Market>> createMarket(@Valid @RequestBody Market market) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Market.ENTITY_NAME).withVersion(Market.ENTITY_VERSION);
            EntityWithMetadata<Market> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, market.getMarketId(), "marketId", Market.class);

            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            EntityWithMetadata<Market> response = entityService.create(market);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create market", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Market>> getMarketById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Market.ENTITY_NAME).withVersion(Market.ENTITY_VERSION);
            EntityWithMetadata<Market> response = entityService.getById(id, modelSpec, Market.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve market", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<PageResult<EntityWithMetadata<Market>>> getAllMarkets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Market.ENTITY_NAME).withVersion(Market.ENTITY_VERSION);
            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();
            
            PageResult<EntityWithMetadata<Market>> result = entityService.findAll(modelSpec, Market.class, params);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve markets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Market>> updateMarket(
            @PathVariable UUID id,
            @Valid @RequestBody Market market,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Market> response = entityService.update(id, market, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update market", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMarket(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete market", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

