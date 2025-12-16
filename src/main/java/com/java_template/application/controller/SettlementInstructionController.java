package com.java_template.application.controller;

import com.java_template.application.entity.settlement_instruction.version_1.SettlementInstruction;
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
 * REST Controller for SettlementInstruction entity
 * Provides settlement management and reconciliation endpoints
 */
@RestController
@RequestMapping("/ui/settlement-instructions")
@CrossOrigin(origins = "*")
public class SettlementInstructionController {

    private static final Logger logger = LoggerFactory.getLogger(SettlementInstructionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SettlementInstructionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<SettlementInstruction>> createInstruction(
            @RequestBody SettlementInstruction instruction) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(SettlementInstruction.ENTITY_NAME).withVersion(SettlementInstruction.ENTITY_VERSION);
            EntityWithMetadata<SettlementInstruction> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, instruction.getSettlementInstructionId(), "settlementInstructionId", SettlementInstruction.class);

            if (existing != null) {
                logger.warn("Settlement instruction with ID {} already exists", instruction.getSettlementInstructionId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Settlement instruction already exists with ID: %s", instruction.getSettlementInstructionId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<SettlementInstruction> response = entityService.create(instruction);
            logger.info("Settlement instruction created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create settlement instruction: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<SettlementInstruction>> getInstructionById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(SettlementInstruction.ENTITY_NAME).withVersion(SettlementInstruction.ENTITY_VERSION);
            EntityWithMetadata<SettlementInstruction> response = entityService.getById(id, modelSpec, SettlementInstruction.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve settlement instruction: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-status")
    public ResponseEntity<PageResult<EntityWithMetadata<SettlementInstruction>>> searchByStatus(
            @RequestParam String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(SettlementInstruction.ENTITY_NAME).withVersion(SettlementInstruction.ENTITY_VERSION);

            SimpleCondition statusCondition = new SimpleCondition()
                    .withJsonPath("$.settlementStatus")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(statusCondition));

            PageResult<EntityWithMetadata<SettlementInstruction>> result = entityService.search(
                    modelSpec,
                    condition,
                    SettlementInstruction.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(size)
                            .pageNumber(page)
                            .build());

            logger.info("Found {} settlement instructions with status '{}'", result.data().size(), status);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search settlement instructions: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-trade")
    public ResponseEntity<EntityWithMetadata<SettlementInstruction>> searchByTrade(@RequestParam String tradeId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(SettlementInstruction.ENTITY_NAME).withVersion(SettlementInstruction.ENTITY_VERSION);

            SimpleCondition tradeCondition = new SimpleCondition()
                    .withJsonPath("$.tradeId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(tradeId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(tradeCondition));

            PageResult<EntityWithMetadata<SettlementInstruction>> result = entityService.search(
                    modelSpec,
                    condition,
                    SettlementInstruction.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .pageNumber(0)
                            .inMemory(true)
                            .build());

            if (result.data().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Found settlement instruction for trade '{}'", tradeId);
            return ResponseEntity.ok(result.data().get(0));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search settlement instructions: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstruction(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Settlement instruction deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete settlement instruction: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

