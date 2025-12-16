package com.java_template.application.controller;

import com.java_template.application.entity.risk_rule.version_1.RiskRule;
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
 * REST Controller for RiskRule entity
 * Provides risk control rule management endpoints
 */
@RestController
@RequestMapping("/ui/risk-rules")
@CrossOrigin(origins = "*")
public class RiskRuleController {

    private static final Logger logger = LoggerFactory.getLogger(RiskRuleController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RiskRuleController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<RiskRule>> createRule(@RequestBody RiskRule rule) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(RiskRule.ENTITY_NAME).withVersion(RiskRule.ENTITY_VERSION);
            EntityWithMetadata<RiskRule> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, rule.getRuleId(), "ruleId", RiskRule.class);

            if (existing != null) {
                logger.warn("Risk rule with ID {} already exists", rule.getRuleId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Risk rule already exists with ID: %s", rule.getRuleId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<RiskRule> response = entityService.create(rule);
            logger.info("Risk rule created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create risk rule: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<RiskRule>> getRuleById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(RiskRule.ENTITY_NAME).withVersion(RiskRule.ENTITY_VERSION);
            EntityWithMetadata<RiskRule> response = entityService.getById(id, modelSpec, RiskRule.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve risk rule: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<RiskRule>> updateRule(
            @PathVariable UUID id,
            @RequestBody RiskRule rule,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<RiskRule> response = entityService.update(id, rule, transition);
            logger.info("Risk rule updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update risk rule: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-type")
    public ResponseEntity<List<EntityWithMetadata<RiskRule>>> searchByType(@RequestParam String type) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(RiskRule.ENTITY_NAME).withVersion(RiskRule.ENTITY_VERSION);

            SimpleCondition typeCondition = new SimpleCondition()
                    .withJsonPath("$.ruleType")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(type));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(typeCondition));

            PageResult<EntityWithMetadata<RiskRule>> result = entityService.search(
                    modelSpec,
                    condition,
                    RiskRule.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(1000)
                            .pageNumber(0)
                            .inMemory(true)
                            .build());

            logger.info("Found {} risk rules of type '{}'", result.data().size(), type);
            return ResponseEntity.ok(result.data());
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search risk rules: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Risk rule deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete risk rule: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

