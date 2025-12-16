package com.java_template.application.controller;

import com.java_template.application.entity.market_data_tick.version_1.MarketDataTick;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for MarketDataTick entity
 * Provides market data ingestion and retrieval endpoints
 */
@RestController
@RequestMapping("/ui/market-data")
@CrossOrigin(origins = "*")
public class MarketDataTickController {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataTickController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MarketDataTickController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/ticks")
    public ResponseEntity<EntityWithMetadata<MarketDataTick>> ingestTick(@RequestBody MarketDataTick tick) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MarketDataTick.ENTITY_NAME).withVersion(MarketDataTick.ENTITY_VERSION);
            EntityWithMetadata<MarketDataTick> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, tick.getTickId(), "tickId", MarketDataTick.class);

            if (existing != null) {
                logger.warn("Tick with ID {} already exists", tick.getTickId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Tick already exists with ID: %s", tick.getTickId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<MarketDataTick> response = entityService.create(tick);
            logger.info("Market data tick created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to ingest market data tick: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/ticks/{id}")
    public ResponseEntity<EntityWithMetadata<MarketDataTick>> getTickById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MarketDataTick.ENTITY_NAME).withVersion(MarketDataTick.ENTITY_VERSION);
            EntityWithMetadata<MarketDataTick> response = entityService.getById(id, modelSpec, MarketDataTick.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve market data tick: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-symbol")
    public ResponseEntity<PageResult<EntityWithMetadata<MarketDataTick>>> searchBySymbol(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MarketDataTick.ENTITY_NAME).withVersion(MarketDataTick.ENTITY_VERSION);

            SimpleCondition symbolCondition = new SimpleCondition()
                    .withJsonPath("$.instrumentSymbol")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(symbol));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(symbolCondition));

            PageResult<EntityWithMetadata<MarketDataTick>> result = entityService.search(
                    modelSpec,
                    condition,
                    MarketDataTick.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(size)
                            .pageNumber(page)
                            .build());

            logger.info("Found {} ticks for symbol '{}'", result.data().size(), symbol);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search market data ticks: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/ticks/{id}")
    public ResponseEntity<Void> deleteTick(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Market data tick deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete market data tick: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

