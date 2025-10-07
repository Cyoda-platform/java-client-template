package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.BatchMode;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for EODAccrualBatch entity operations, providing CRUD endpoints
 * for managing end-of-day accrual batch runs throughout their lifecycle.
 */
@RestController
@RequestMapping("/ui/eod-batches")
@CrossOrigin(origins = "*")
public class EODAccrualBatchController {

    private static final Logger logger = LoggerFactory.getLogger(EODAccrualBatchController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EODAccrualBatchController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new EOD accrual batch
     * POST /ui/eod-batches
     */
    @PostMapping
    public ResponseEntity<?> createBatch(@RequestBody EODAccrualBatch batch) {
        try {
            // Note: EODAccrualBatch uses UUID batchId which is auto-generated
            // No duplicate check needed as UUID is unique

            EntityWithMetadata<EODAccrualBatch> response = entityService.create(batch);
            logger.info("EODAccrualBatch created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create batch: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch by technical UUID
     * GET /ui/eod-batches/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> getBatchById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EODAccrualBatch.ENTITY_NAME).withVersion(EODAccrualBatch.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<EODAccrualBatch> response = entityService.getById(id, modelSpec, EODAccrualBatch.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch by business identifier
     * GET /ui/eod-batches/business/{batchId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{batchId}")
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> getBatchByBusinessId(
            @PathVariable UUID batchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EODAccrualBatch.ENTITY_NAME).withVersion(EODAccrualBatch.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<EODAccrualBatch> response = entityService.findByBusinessId(
                    modelSpec, batchId.toString(), "batchId", EODAccrualBatch.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve batch with business ID '%s': %s", batchId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch change history metadata
     * GET /ui/eod-batches/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<?> getBatchChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            List<EntityChangeMeta> changes =
                    entityService.getEntityChangesMetadata(id, pointInTimeDate);
            return ResponseEntity.ok(changes);
        } catch (Exception e) {
            // Check if it's a NOT_FOUND error (entity doesn't exist)
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve change history for batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update batch with optional workflow transition
     * PUT /ui/eod-batches/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> updateBatch(
            @PathVariable UUID id,
            @RequestBody EODAccrualBatch batch,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<EODAccrualBatch> response = entityService.update(id, batch, transition);
            logger.info("EODAccrualBatch updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all batches with pagination and optional filtering
     * GET /ui/eod-batches?page=0&size=20&state=COMPLETED&asOfDate=2025-10-07&mode=TODAY&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<?> listBatches(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) BatchMode mode,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EODAccrualBatch.ENTITY_NAME).withVersion(EODAccrualBatch.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (asOfDate != null) {
                SimpleCondition dateCondition = new SimpleCondition()
                        .withJsonPath("$.asOfDate")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(asOfDate));
                conditions.add(dateCondition);
            }

            if (mode != null) {
                SimpleCondition modeCondition = new SimpleCondition()
                        .withJsonPath("$.mode")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(mode.name()));
                conditions.add(modeCondition);
            }

            if (conditions.isEmpty() && (state == null || state.trim().isEmpty())) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, EODAccrualBatch.class, pointInTimeDate));
            } else {
                // For filtered results, use search (returns all matching results, not paginated)
                List<EntityWithMetadata<EODAccrualBatch>> batches;
                if (conditions.isEmpty()) {
                    batches = entityService.findAll(modelSpec, EODAccrualBatch.class, pointInTimeDate);
                } else {
                    GroupCondition groupCondition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    batches = entityService.search(modelSpec, groupCondition, EODAccrualBatch.class, pointInTimeDate);
                }

                // Filter by state if provided (state is in metadata, not entity)
                if (state != null && !state.trim().isEmpty()) {
                    batches = batches.stream()
                            .filter(batch -> state.equals(batch.metadata().getState()))
                            .toList();
                }

                return ResponseEntity.ok(batches);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list batches: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete batch by technical UUID
     * DELETE /ui/eod-batches/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBatch(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("EODAccrualBatch deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete batch by business identifier
     * DELETE /ui/eod-batches/business/{batchId}
     */
    @DeleteMapping("/business/{batchId}")
    public ResponseEntity<Void> deleteBatchByBusinessId(@PathVariable UUID batchId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EODAccrualBatch.ENTITY_NAME).withVersion(EODAccrualBatch.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, batchId.toString(), "batchId", EODAccrualBatch.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("EODAccrualBatch deleted with business ID: {}", batchId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete batch with business ID '%s': %s", batchId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete all batches (DANGEROUS - use with caution)
     * DELETE /ui/eod-batches
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAllBatches() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EODAccrualBatch.ENTITY_NAME).withVersion(EODAccrualBatch.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all EODAccrualBatches - count: {}", deletedCount);
            return ResponseEntity.ok().body(String.format("Deleted %d batches", deletedCount));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete all batches: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

