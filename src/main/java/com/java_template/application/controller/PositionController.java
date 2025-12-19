package com.java_template.application.controller;

import com.java_template.application.entity.position.version_1.Position;
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
 * PositionController - REST API for position management
 * Handles position tracking, P&L calculations, and risk metrics
 */
@RestController
@RequestMapping("/ui/position")
@CrossOrigin(origins = "*")
public class PositionController {

    private static final Logger logger = LoggerFactory.getLogger(PositionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PositionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new position
     * POST /ui/position
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Position>> createPosition(@Valid @RequestBody Position position) {
        try {
            // Check for duplicate business identifier
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

    /**
     * Get position by technical UUID
     * GET /ui/position/{id}
     */
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
                String.format("Failed to retrieve position with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update position
     * PUT /ui/position/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Position>> updatePosition(
            @PathVariable UUID id,
            @Valid @RequestBody Position position) {
        try {
            EntityWithMetadata<Position> response = entityService.update(id, position);
            logger.info("Position updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update position with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get position by business identifier
     * GET /ui/position/business/{positionId}
     */
    @GetMapping("/business/{positionId}")
    public ResponseEntity<EntityWithMetadata<Position>> getPositionByBusinessId(@PathVariable String positionId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Position.ENTITY_NAME).withVersion(Position.ENTITY_VERSION);
            EntityWithMetadata<Position> response = entityService.findByBusinessId(
                    modelSpec, positionId, "positionId", Position.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve position with business ID '%s': %s", positionId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

