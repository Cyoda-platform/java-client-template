package com.java_template.application.processor.eod_batch;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.BatchMode;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Processor to recompute forward days and post deltas for back-dated runs.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Determines cascade date range from batch's asOfDate to current business date</li>
 *   <li>For each date in range, identifies affected loans</li>
 *   <li>Triggers recalculation of accruals for subsequent dates</li>
 *   <li>Updates batch's cascadeFromDate field</li>
 * </ul>
 *
 * <p>Only executes for BACKDATED mode batches.</p>
 *
 * <p>Execution Mode: ASYNC_NEW_TX</p>
 * <p>Calculation Nodes Tags: recalc</p>
 */
@Component
public class SpawnCascadeRecalcProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SpawnCascadeRecalcProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SpawnCascadeRecalcProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SpawnCascadeRecalc for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(EODAccrualBatch.class)
            .validate(this::isValidEntityWithMetadata, "Invalid batch entity")
            .map(this::spawnCascadeRecalcLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SpawnCascadeRecalc".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EODAccrualBatch> entityWithMetadata) {
        EODAccrualBatch batch = entityWithMetadata.entity();
        return batch != null && batch.isValid() && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to spawn cascade recalculation.
     *
     * <p>CRITICAL LIMITATIONS:</p>
     * <ul>
     *   <li>✅ ALLOWED: Read current batch data</li>
     *   <li>✅ ALLOWED: Query and update Accrual entities via EntityService</li>
     *   <li>✅ ALLOWED: Update batch cascadeFromDate field</li>
     *   <li>❌ FORBIDDEN: Update current batch state/transitions</li>
     * </ul>
     */
    private EntityWithMetadata<EODAccrualBatch> spawnCascadeRecalcLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EODAccrualBatch> context) {

        EntityWithMetadata<EODAccrualBatch> entityWithMetadata = context.entityResponse();
        EODAccrualBatch batch = entityWithMetadata.entity();

        UUID batchId = batch.getBatchId();
        LocalDate asOfDate = batch.getAsOfDate();
        BatchMode mode = batch.getMode();

        // Only process for BACKDATED mode
        if (mode != BatchMode.BACKDATED) {
            logger.info("Batch {} is not BACKDATED mode, skipping cascade recalc", batchId);
            return entityWithMetadata;
        }

        if (asOfDate == null) {
            logger.error("AsOfDate is null for batch: {}", batchId);
            throw new IllegalStateException("AsOfDate is required for cascade recalc");
        }

        logger.debug("Spawning cascade recalc for batch: {} with asOfDate: {}", batchId, asOfDate);

        // Determine cascade date range
        LocalDate currentBusinessDate = LocalDate.now(); // TODO: Use business calendar
        LocalDate cascadeFromDate = asOfDate.plusDays(1); // Start from day after backdated run

        if (!cascadeFromDate.isBefore(currentBusinessDate)) {
            logger.info("No cascade needed - asOfDate {} is current or future", asOfDate);
            return entityWithMetadata;
        }

        logger.info("Cascade range: {} to {}", cascadeFromDate, currentBusinessDate);

        // Query accruals created by this batch to identify affected loans
        Set<String> affectedLoanIds = identifyAffectedLoans(batchId);

        logger.info("Found {} affected loans for cascade recalc", affectedLoanIds.size());

        // Trigger recalculation for subsequent dates
        int recalculationsTriggered = triggerCascadeRecalculations(
            affectedLoanIds, cascadeFromDate, currentBusinessDate);

        // Update batch's cascadeFromDate
        batch.setCascadeFromDate(cascadeFromDate);

        logger.info("Batch {} triggered {} cascade recalculations from {}",
            batchId, recalculationsTriggered, cascadeFromDate);

        return entityWithMetadata;
    }

    /**
     * Identifies loans affected by this batch's accruals.
     *
     * @param runId The batch's batchId
     * @return Set of loan IDs that need cascade recalculation
     */
    private Set<String> identifyAffectedLoans(UUID runId) {
        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        // Query all accruals with this runId
        List<EntityWithMetadata<Accrual>> accrualsWithMetadata = 
            entityService.findAll(accrualModelSpec, Accrual.class);

        Set<String> affectedLoanIds = new HashSet<>();
        for (EntityWithMetadata<Accrual> accrualWithMetadata : accrualsWithMetadata) {
            Accrual accrual = accrualWithMetadata.entity();
            if (runId.toString().equals(accrual.getRunId())) {
                affectedLoanIds.add(accrual.getLoanId());
            }
        }

        return affectedLoanIds;
    }

    /**
     * Triggers recalculation of accruals for affected loans in the cascade range.
     *
     * <p>TODO: Implement actual cascade recalculation logic.</p>
     * <p>This could involve:</p>
     * <ul>
     *   <li>Finding existing accruals for affected loans in the date range</li>
     *   <li>Marking them for recalculation (e.g., transition to REBOOK state)</li>
     *   <li>Creating new accruals that supersede the old ones</li>
     * </ul>
     *
     * @param affectedLoanIds Loans that need recalculation
     * @param fromDate Start of cascade range (inclusive)
     * @param toDate End of cascade range (exclusive)
     * @return Number of recalculations triggered
     */
    private int triggerCascadeRecalculations(
            Set<String> affectedLoanIds, LocalDate fromDate, LocalDate toDate) {

        int recalculationsTriggered = 0;

        // TODO: Implement cascade recalculation logic
        // For now, just log the intent
        logger.info("Would trigger recalculations for {} loans from {} to {}",
            affectedLoanIds.size(), fromDate, toDate);

        // Example implementation:
        // 1. For each date in range [fromDate, toDate)
        // 2. For each affected loan
        // 3. Find existing accrual for (loanId, date)
        // 4. If exists and not already superseded:
        //    - Create new accrual with supersedesAccrualId pointing to old one
        //    - Trigger workflow to recalculate
        //    - recalculationsTriggered++

        return recalculationsTriggered;
    }
}

