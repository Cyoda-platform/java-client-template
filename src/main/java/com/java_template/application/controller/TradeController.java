package com.java_template.application.controller;

import com.java_template.application.entity.trade.version_1.Trade;
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
 * REST Controller for Trade entity
 * Provides trade retrieval and settlement endpoints
 */
@RestController
@RequestMapping("/ui/trades")
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
    public ResponseEntity<EntityWithMetadata<Trade>> createTrade(@RequestBody Trade trade) {
        try {
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
                String.format("Failed to retrieve trade: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-portfolio")
    public ResponseEntity<PageResult<EntityWithMetadata<Trade>>> searchByPortfolio(
            @RequestParam String portfolioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);

            SimpleCondition portfolioCondition = new SimpleCondition()
                    .withJsonPath("$.portfolioId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(portfolioId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(portfolioCondition));

            PageResult<EntityWithMetadata<Trade>> result = entityService.search(
                    modelSpec,
                    condition,
                    Trade.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(size)
                            .pageNumber(page)
                            .build());

            logger.info("Found {} trades for portfolio '{}'", result.data().size(), portfolioId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search trades: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-status")
    public ResponseEntity<List<EntityWithMetadata<Trade>>> searchBySettlementStatus(
            @RequestParam String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Trade.ENTITY_NAME).withVersion(Trade.ENTITY_VERSION);

            SimpleCondition statusCondition = new SimpleCondition()
                    .withJsonPath("$.settlementStatus")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(statusCondition));

            PageResult<EntityWithMetadata<Trade>> result = entityService.search(
                    modelSpec,
                    condition,
                    Trade.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(1000)
                            .pageNumber(0)
                            .inMemory(true)
                            .build());

            logger.info("Found {} trades with status '{}'", result.data().size(), status);
            return ResponseEntity.ok(result.data());
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search trades: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrade(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Trade deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete trade: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

