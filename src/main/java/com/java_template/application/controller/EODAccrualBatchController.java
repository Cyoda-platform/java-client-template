package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.dto.EngineOptions;
import com.java_template.application.controller.dto.TransitionRequest;
import com.java_template.application.entity.accrual.version_1.BatchMode;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
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
 * REST controller for EODAccrualBatch entity operations.
 * 
 * <p>Provides CRUD endpoints for managing end-of-day accrual batch runs.
 * Supports workflow transitions for batch lifecycle management including
 * starting batch runs, monitoring progress, and handling failures.</p>
 * 
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /eod-batches - Create new batch with optional workflow transition (e.g., START)</li>
 *   <li>GET /eod-batches/{batchId} - Retrieve batch by technical UUID</li>
 *   <li>PATCH /eod-batches/{batchId} - Update batch with optional workflow transition</li>
 *   <li>GET /eod-batches - Query batches with filters (asOfDate, mode, state)</li>
 * </ul>
 * 
 * @see EODAccrualBatch
 * @see TransitionRequest
 * @see EngineOptions
 */
@RestController
@RequestMapping("/eod-batches")
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
     * POST /eod-batches
     * 
     * <p>Request body supports:</p>
     * <ul>
     *   <li>batch - The batch entity data (required)</li>
     *   <li>transitionRequest - Optional workflow transition to trigger after creation (e.g., "START")</li>
     *   <li>engineOptions - Optional engine execution options (simulate, maxSteps)</li>
     * </ul>
     * 
     * <p>Example request (from section 7.1 of requirements):</p>
     * <pre>
     * {
     *   "batch": {
     *     "asOfDate": "2025-08-15",
     *     "mode": "BACKDATED",
     *     "reasonCode": "DATA_CORRECTION"
     *   },
     *   "transitionRequest": { "name": "START" },
     *   "engineOptions": { "simulate": false, "maxSteps": 50 }
     * }
     * </pre>
     * 
     * @param request The create batch request containing batch data and optional transition
     * @return 201 Created with Location header and created batch with metadata
     */
    @PostMapping
    public ResponseEntity<?> createBatch(@RequestBody CreateBatchRequest request) {
        try {
            // Validate request
            if (request.getBatch() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Batch data is required"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EODAccrualBatch batch = request.getBatch();
            
            // Note: EODAccrualBatch uses UUID batchId, not a business identifier string
            // So we don't check for duplicates the same way as Accrual
            // The batchId will be auto-generated if not provided
            
            // Create the batch
            EntityWithMetadata<EODAccrualBatch> response = entityService.create(batch);
            logger.info("EODAccrualBatch created with ID: {}", response.metadata().getId());

            // Apply transition if requested (typically "START" to begin the batch run)
            if (request.getTransitionRequest() != null && request.getTransitionRequest().getName() != null) {
                String transitionName = request.getTransitionRequest().getName();
                String comment = request.getTransitionRequest().getComment();
                
                logger.info("Applying transition '{}' to batch {}. Comment: {}", 
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
            logger.error("Failed to create EODAccrualBatch", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create batch: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch by technical UUID
     * GET /eod-batches/{batchId}
     * 
     * <p>Returns the batch with current state and metrics including:</p>
     * <ul>
     *   <li>Current workflow state</li>
     *   <li>Progress metrics (eligible loans, processed, failed)</li>
     *   <li>Reconciliation report ID (if completed)</li>
     * </ul>
     * 
     * @param batchId Technical UUID of the batch
     * @return 200 OK with batch and metadata, or 404 Not Found
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<?> getBatchById(@PathVariable UUID batchId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(EODAccrualBatch.ENTITY_NAME)
                .withVersion(EODAccrualBatch.ENTITY_VERSION);
            
            EntityWithMetadata<EODAccrualBatch> response = entityService.getById(
                batchId, modelSpec, EODAccrualBatch.class, null);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve batch with ID: {}", batchId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update batch with optional workflow transition
     * PATCH /eod-batches/{batchId}
     * 
     * <p>Request body supports:</p>
     * <ul>
     *   <li>batch - Partial or full batch entity data (required)</li>
     *   <li>transitionRequest - Optional workflow transition to trigger after update</li>
     *   <li>engineOptions - Optional engine execution options (simulate, maxSteps)</li>
     * </ul>
     * 
     * <p>Common transitions:</p>
     * <ul>
     *   <li>START - Begin batch processing</li>
     *   <li>CANCEL - Cancel a running batch</li>
     *   <li>RETRY - Retry failed accruals</li>
     * </ul>
     * 
     * @param batchId Technical UUID of the batch to update
     * @param request The update request containing batch data and optional transition
     * @return 200 OK with updated batch and metadata
     */
    @PatchMapping("/{batchId}")
    public ResponseEntity<?> updateBatch(
            @PathVariable UUID batchId,
            @RequestBody UpdateBatchRequest request) {
        try {
            // Validate request
            if (request.getBatch() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Batch data is required"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EODAccrualBatch batch = request.getBatch();
            String transitionName = null;
            
            if (request.getTransitionRequest() != null && request.getTransitionRequest().getName() != null) {
                transitionName = request.getTransitionRequest().getName();
                String comment = request.getTransitionRequest().getComment();
                
                logger.info("Updating batch {} with transition '{}'. Comment: {}", 
                    batchId, transitionName, comment);
            }
            
            // TODO: Pass engineOptions to EntityService when framework supports it
            EntityWithMetadata<EODAccrualBatch> response = entityService.update(
                batchId, batch, transitionName);
            
            logger.info("EODAccrualBatch updated with ID: {}", batchId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update batch with ID: {}", batchId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update batch with ID '%s': %s", batchId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Query batches with optional filters
     * GET /eod-batches?asOfDate=2025-10-07&mode=TODAY&state=COMPLETED
     * 
     * <p>All query parameters are optional. Returns all batches if no filters provided.</p>
     * 
     * @param asOfDate Optional filter by as-of date
     * @param mode Optional filter by batch mode (TODAY or BACKDATED)
     * @param state Optional filter by workflow state
     * @return 200 OK with list of matching batches
     */
    @GetMapping
    public ResponseEntity<?> queryBatches(
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) BatchMode mode,
            @RequestParam(required = false) String state) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(EODAccrualBatch.ENTITY_NAME)
                .withVersion(EODAccrualBatch.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            // Add entity field filters
            if (asOfDate != null) {
                conditions.add(new SimpleCondition()
                    .withJsonPath("$.asOfDate")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(asOfDate)));
            }

            if (mode != null) {
                conditions.add(new SimpleCondition()
                    .withJsonPath("$.mode")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(mode.name())));
            }

            List<EntityWithMetadata<EODAccrualBatch>> batches;
            
            if (conditions.isEmpty()) {
                // No entity field filters - use findAll
                batches = entityService.findAll(modelSpec, EODAccrualBatch.class, null);
            } else {
                // Apply entity field filters via search
                GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);
                batches = entityService.search(modelSpec, groupCondition, EODAccrualBatch.class, null);
            }

            // Filter by state if provided (state is in metadata, not entity)
            if (state != null && !state.trim().isEmpty()) {
                batches = batches.stream()
                    .filter(batch -> state.equals(batch.metadata().getState()))
                    .toList();
            }

            return ResponseEntity.ok(batches);
            
        } catch (Exception e) {
            logger.error("Failed to query batches", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to query batches: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * DTO for create batch requests
     */
    @Data
    public static class CreateBatchRequest {
        private EODAccrualBatch batch;
        private TransitionRequest transitionRequest;
        private EngineOptions engineOptions;
    }

    /**
     * DTO for update batch requests
     */
    @Data
    public static class UpdateBatchRequest {
        private EODAccrualBatch batch;
        private TransitionRequest transitionRequest;
        private EngineOptions engineOptions;
    }
}

