package com.java_template.application.controller;

import com.java_template.application.entity.trade.version_1.Trade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * TradeController - REST API for Trade entity management
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

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Trade>> createTrade(@Valid @RequestBody Trade trade) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);
            EntityWithMetadata<Trade> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, trade.getTradeId(), "tradeId", Trade.class);

            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            EntityWithMetadata<Trade> response = entityService.create(trade);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create trade", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Trade>> getTradeById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);
            EntityWithMetadata<Trade> response = entityService.getById(id, modelSpec, Trade.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve trade", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/market/{marketId}")
    public ResponseEntity<PageResult<EntityWithMetadata<Trade>>> getTradesByMarketId(
            @PathVariable String marketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.marketId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(marketId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<Trade>> result = entityService.search(
                    modelSpec, groupCondition, Trade.class, params);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve trades", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Trade>> updateTrade(
            @PathVariable UUID id,
            @Valid @RequestBody Trade trade,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Trade> response = entityService.update(id, trade, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update trade", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrade(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete trade", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

