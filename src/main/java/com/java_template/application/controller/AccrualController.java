package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.Accrual;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
 * ABOUTME: REST controller for Accrual entity operations, providing CRUD endpoints
 * for managing daily interest accruals on loans throughout their lifecycle.
 */
@RestController
@RequestMapping("/ui/accruals")
@CrossOrigin(origins = "*")
public class AccrualController {

    private static final Logger logger = LoggerFactory.getLogger(AccrualController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AccrualController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new accrual
     * POST /ui/accruals
     */
    @PostMapping
    public ResponseEntity<?> createAccrual(@RequestBody Accrual accrual) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
            EntityWithMetadata<Accrual> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, accrual.getAccrualId(), "accrualId", Accrual.class);

            if (existing != null) {
                logger.warn("Accrual with business ID {} already exists", accrual.getAccrualId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Accrual already exists with ID: %s", accrual.getAccrualId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Accrual> response = entityService.create(accrual);
            logger.info("Accrual created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create accrual: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get accrual by technical UUID
     * GET /ui/accruals/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Accrual>> getAccrualById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Accrual> response = entityService.getById(id, modelSpec, Accrual.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve accrual with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get accrual by business identifier
     * GET /ui/accruals/business/{accrualId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{accrualId}")
    public ResponseEntity<EntityWithMetadata<Accrual>> getAccrualByBusinessId(
            @PathVariable String accrualId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Accrual> response = entityService.findByBusinessId(
                    modelSpec, accrualId, "accrualId", Accrual.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve accrual with business ID '%s': %s", accrualId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get accrual change history metadata
     * GET /ui/accruals/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<?> getAccrualChangesMetadata(
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
                String.format("Failed to retrieve change history for accrual with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update accrual with optional workflow transition
     * PUT /ui/accruals/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Accrual>> updateAccrual(
            @PathVariable UUID id,
            @RequestBody Accrual accrual,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Accrual> response = entityService.update(id, accrual, transition);
            logger.info("Accrual updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update accrual with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all accruals with pagination and optional filtering
     * GET /ui/accruals?page=0&size=20&state=POSTED&loanId=LOAN-123&asOfDate=2025-10-07&runId=RUN-001&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Accrual>>> listAccruals(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (loanId != null && !loanId.trim().isEmpty()) {
                SimpleCondition loanCondition = new SimpleCondition()
                        .withJsonPath("$.loanId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(loanId));
                conditions.add(loanCondition);
            }

            if (asOfDate != null) {
                SimpleCondition dateCondition = new SimpleCondition()
                        .withJsonPath("$.asOfDate")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(asOfDate));
                conditions.add(dateCondition);
            }

            if (runId != null && !runId.trim().isEmpty()) {
                SimpleCondition runCondition = new SimpleCondition()
                        .withJsonPath("$.runId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(runId));
                conditions.add(runCondition);
            }

            if (conditions.isEmpty() && (state == null || state.trim().isEmpty())) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Accrual.class, pointInTimeDate));
            } else {
                // For filtered results, get all matching results then manually paginate
                List<EntityWithMetadata<Accrual>> accruals;
                if (conditions.isEmpty()) {
                    accruals = entityService.findAll(modelSpec, Accrual.class, pointInTimeDate);
                } else {
                    GroupCondition groupCondition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    accruals = entityService.search(modelSpec, groupCondition, Accrual.class, pointInTimeDate);
                }

                // Filter by state if provided (state is in metadata, not entity)
                if (state != null && !state.trim().isEmpty()) {
                    accruals = accruals.stream()
                            .filter(accrual -> state.equals(accrual.metadata().getState()))
                            .toList();
                }

                // Manually paginate the filtered results
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), accruals.size());
                List<EntityWithMetadata<Accrual>> pageContent = start < accruals.size()
                    ? accruals.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Accrual>> page = new PageImpl<>(pageContent, pageable, accruals.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list accruals: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete accrual by technical UUID
     * DELETE /ui/accruals/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccrual(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Accrual deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete accrual with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete accrual by business identifier
     * DELETE /ui/accruals/business/{accrualId}
     */
    @DeleteMapping("/business/{accrualId}")
    public ResponseEntity<Void> deleteAccrualByBusinessId(@PathVariable String accrualId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, accrualId, "accrualId", Accrual.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Accrual deleted with business ID: {}", accrualId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete accrual with business ID '%s': %s", accrualId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete all accruals (DANGEROUS - use with caution)
     * DELETE /ui/accruals
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAllAccruals() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all Accruals - count: {}", deletedCount);
            return ResponseEntity.ok().body(String.format("Deleted %d accruals", deletedCount));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete all accruals: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

