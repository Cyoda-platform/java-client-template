package com.java_template.application.controller;

import com.java_template.application.entity.liquiditypool.version_1.LiquidityPool;
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
 * LiquidityPoolController - REST API for Liquidity Pool entity management
 */
@RestController
@RequestMapping("/ui/liquidity-pool")
@CrossOrigin(origins = "*")
public class LiquidityPoolController {

    private static final Logger logger = LoggerFactory.getLogger(LiquidityPoolController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LiquidityPoolController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<LiquidityPool>> createPool(@Valid @RequestBody LiquidityPool pool) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(LiquidityPool.ENTITY_NAME).withVersion(LiquidityPool.ENTITY_VERSION);
            EntityWithMetadata<LiquidityPool> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, pool.getPoolId(), "poolId", LiquidityPool.class);

            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            EntityWithMetadata<LiquidityPool> response = entityService.create(pool);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create liquidity pool", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<LiquidityPool>> getPoolById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(LiquidityPool.ENTITY_NAME).withVersion(LiquidityPool.ENTITY_VERSION);
            EntityWithMetadata<LiquidityPool> response = entityService.getById(id, modelSpec, LiquidityPool.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve liquidity pool", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/market/{marketId}")
    public ResponseEntity<PageResult<EntityWithMetadata<LiquidityPool>>> getPoolsByMarketId(
            @PathVariable String marketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(LiquidityPool.ENTITY_NAME).withVersion(LiquidityPool.ENTITY_VERSION);
            
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

            PageResult<EntityWithMetadata<LiquidityPool>> result = entityService.search(
                    modelSpec, groupCondition, LiquidityPool.class, params);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve liquidity pools", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PageResult<EntityWithMetadata<LiquidityPool>>> getPoolsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(LiquidityPool.ENTITY_NAME).withVersion(LiquidityPool.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<LiquidityPool>> result = entityService.search(
                    modelSpec, groupCondition, LiquidityPool.class, params);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve liquidity pools", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<LiquidityPool>> updatePool(
            @PathVariable UUID id,
            @Valid @RequestBody LiquidityPool pool,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<LiquidityPool> response = entityService.update(id, pool, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update liquidity pool", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePool(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete liquidity pool", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

