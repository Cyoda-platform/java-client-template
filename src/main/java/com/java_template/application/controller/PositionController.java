package com.java_template.application.controller;

import com.java_template.application.entity.position.version_1.Position;
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
 * REST Controller for Position entity
 * Provides position management and P&L tracking endpoints
 */
@RestController
@RequestMapping("/ui/positions")
@CrossOrigin(origins = "*")
public class PositionController {

    private static final Logger logger = LoggerFactory.getLogger(PositionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PositionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Position>> createPosition(@RequestBody Position position) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Position.ENTITY_NAME).withVersion(Position.ENTITY_VERSION);
            EntityWithMetadata<Position> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, position.getPositionId(), "positionId", Position.class);

            if (existing != null) {
                logger.warn("Position with ID {} already exists", position.getPositionId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Position already exists with ID: %s", position.getPositionId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Position> response = entityService.create(position);
            logger.info("Position created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create position: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Position>> getPositionById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Position.ENTITY_NAME).withVersion(Position.ENTITY_VERSION);
            EntityWithMetadata<Position> response = entityService.getById(id, modelSpec, Position.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve position: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Position>> updatePosition(
            @PathVariable UUID id,
            @RequestBody Position position,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Position> response = entityService.update(id, position, transition);
            logger.info("Position updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update position: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-portfolio")
    public ResponseEntity<PageResult<EntityWithMetadata<Position>>> searchByPortfolio(
            @RequestParam String portfolioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Position.ENTITY_NAME).withVersion(Position.ENTITY_VERSION);

            SimpleCondition portfolioCondition = new SimpleCondition()
                    .withJsonPath("$.portfolioId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(portfolioId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(portfolioCondition));

            PageResult<EntityWithMetadata<Position>> result = entityService.search(
                    modelSpec,
                    condition,
                    Position.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(size)
                            .pageNumber(page)
                            .build());

            logger.info("Found {} positions for portfolio '{}'", result.data().size(), portfolioId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search positions: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/mark-to-market")
    public ResponseEntity<EntityWithMetadata<Position>> markToMarket(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Position.ENTITY_NAME).withVersion(Position.ENTITY_VERSION);
            EntityWithMetadata<Position> current = entityService.getById(id, modelSpec, Position.class);

            EntityWithMetadata<Position> response = entityService.update(id, current.entity(), "mark_to_market");
            logger.info("Position marked to market with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to mark position to market: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePosition(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Position deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete position: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

