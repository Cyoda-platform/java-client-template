package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.dto.EngineOptions;
import com.java_template.application.controller.dto.TransitionRequest;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Accrual entity operations.
 * 
 * <p>Provides CRUD endpoints for managing daily interest accruals on loans.
 * Supports workflow transitions for accrual lifecycle management.</p>
 * 
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /accruals - Create new accrual with optional workflow transition</li>
 *   <li>GET /accruals/{accrualId} - Retrieve accrual by technical UUID</li>
 *   <li>PATCH /accruals/{accrualId} - Update accrual with optional workflow transition</li>
 *   <li>GET /accruals - Query accruals with filters (loanId, asOfDate, state, runId)</li>
 * </ul>
 * 
 * @see Accrual
 * @see TransitionRequest
 * @see EngineOptions
 */
@RestController
@RequestMapping("/accruals")
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
     * POST /accruals
     * 
     * <p>Request body supports:</p>
     * <ul>
     *   <li>accrual - The accrual entity data (required)</li>
     *   <li>transitionRequest - Optional workflow transition to trigger after creation</li>
     *   <li>engineOptions - Optional engine execution options (simulate, maxSteps)</li>
     * </ul>
     * 
     * <p>Example request:</p>
     * <pre>
     * {
     *   "accrual": {
     *     "accrualId": "ACC-2025-001",
     *     "loanId": "LOAN-123",
     *     "asOfDate": "2025-10-07",
     *     "currency": "USD"
     *   },
     *   "transitionRequest": { "name": "CALCULATE", "comment": "Auto-calculate" },
     *   "engineOptions": { "simulate": false, "maxSteps": 50 }
     * }
     * </pre>
     * 
     * @param request The create accrual request containing accrual data and optional transition
     * @return 201 Created with Location header and created accrual with metadata
     */
    @PostMapping
    public ResponseEntity<?> createAccrual(@RequestBody CreateAccrualRequest request) {
        try {
            // Validate request
            if (request.getAccrual() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Accrual data is required"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Accrual accrual = request.getAccrual();
            
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec()
                .withName(Accrual.ENTITY_NAME)
                .withVersion(Accrual.ENTITY_VERSION);
            
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

            // Create the accrual
            EntityWithMetadata<Accrual> response = entityService.create(accrual);
            logger.info("Accrual created with ID: {}", response.metadata().getId());

            // Apply transition if requested
            if (request.getTransitionRequest() != null && request.getTransitionRequest().getName() != null) {
                String transitionName = request.getTransitionRequest().getName();
                String comment = request.getTransitionRequest().getComment();
                
                logger.info("Applying transition '{}' to accrual {}. Comment: {}", 
                    transitionName, response.metadata().getId(), comment);
                
                // TODO: Pass engineOptions to EntityService when framework supports it
                // Currently only transition name is supported
                response = entityService.update(
                    response.metadata().getId(), 
                    response.entity(), 
                    transitionName
                );
            }

            // Build Location header for the created resource
            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
            
        } catch (Exception e) {
            logger.error("Failed to create accrual", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create accrual: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get accrual by technical UUID
     * GET /accruals/{accrualId}
     * 
     * @param accrualId Technical UUID of the accrual
     * @return 200 OK with accrual and metadata, or 404 Not Found
     */
    @GetMapping("/{accrualId}")
    public ResponseEntity<?> getAccrualById(@PathVariable UUID accrualId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Accrual.ENTITY_NAME)
                .withVersion(Accrual.ENTITY_VERSION);
            
            EntityWithMetadata<Accrual> response = entityService.getById(
                accrualId, modelSpec, Accrual.class, null);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve accrual with ID: {}", accrualId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update accrual with optional workflow transition
     * PATCH /accruals/{accrualId}
     * 
     * <p>Request body supports:</p>
     * <ul>
     *   <li>accrual - Partial or full accrual entity data (required)</li>
     *   <li>transitionRequest - Optional workflow transition to trigger after update</li>
     *   <li>engineOptions - Optional engine execution options (simulate, maxSteps)</li>
     * </ul>
     * 
     * @param accrualId Technical UUID of the accrual to update
     * @param request The update request containing accrual data and optional transition
     * @return 200 OK with updated accrual and metadata
     */
    @PatchMapping("/{accrualId}")
    public ResponseEntity<?> updateAccrual(
            @PathVariable UUID accrualId,
            @RequestBody UpdateAccrualRequest request) {
        try {
            // Validate request
            if (request.getAccrual() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Accrual data is required"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Accrual accrual = request.getAccrual();
            String transitionName = null;
            
            if (request.getTransitionRequest() != null && request.getTransitionRequest().getName() != null) {
                transitionName = request.getTransitionRequest().getName();
                String comment = request.getTransitionRequest().getComment();
                
                logger.info("Updating accrual {} with transition '{}'. Comment: {}", 
                    accrualId, transitionName, comment);
            }
            
            // TODO: Pass engineOptions to EntityService when framework supports it
            EntityWithMetadata<Accrual> response = entityService.update(
                accrualId, accrual, transitionName);
            
            logger.info("Accrual updated with ID: {}", accrualId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update accrual with ID: {}", accrualId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update accrual with ID '%s': %s", accrualId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Query accruals with optional filters
     * GET /accruals?loanId=LOAN-123&asOfDate=2025-10-07&state=POSTED&runId=RUN-001
     * 
     * <p>All query parameters are optional. Returns all accruals if no filters provided.</p>
     * 
     * @param loanId Optional filter by loan ID
     * @param asOfDate Optional filter by as-of date
     * @param state Optional filter by workflow state
     * @param runId Optional filter by batch run ID
     * @return 200 OK with list of matching accruals
     */
    @GetMapping
    public ResponseEntity<?> queryAccruals(
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String runId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Accrual.ENTITY_NAME)
                .withVersion(Accrual.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            // Add entity field filters
            if (loanId != null && !loanId.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                    .withJsonPath("$.loanId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(loanId)));
            }

            if (asOfDate != null) {
                conditions.add(new SimpleCondition()
                    .withJsonPath("$.asOfDate")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(asOfDate)));
            }

            if (runId != null && !runId.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                    .withJsonPath("$.runId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(runId)));
            }

            List<EntityWithMetadata<Accrual>> accruals;
            
            if (conditions.isEmpty()) {
                // No entity field filters - use findAll
                accruals = entityService.findAll(modelSpec, Accrual.class, null);
            } else {
                // Apply entity field filters via search
                GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);
                accruals = entityService.search(modelSpec, groupCondition, Accrual.class, null);
            }

            // Filter by state if provided (state is in metadata, not entity)
            if (state != null && !state.trim().isEmpty()) {
                accruals = accruals.stream()
                    .filter(accrual -> state.equals(accrual.metadata().getState()))
                    .toList();
            }

            return ResponseEntity.ok(accruals);
            
        } catch (Exception e) {
            logger.error("Failed to query accruals", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to query accruals: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * DTO for create accrual requests
     */
    @Data
    public static class CreateAccrualRequest {
        private Accrual accrual;
        private TransitionRequest transitionRequest;
        private EngineOptions engineOptions;
    }

    /**
     * DTO for update accrual requests
     */
    @Data
    public static class UpdateAccrualRequest {
        private Accrual accrual;
        private TransitionRequest transitionRequest;
        private EngineOptions engineOptions;
    }
}

