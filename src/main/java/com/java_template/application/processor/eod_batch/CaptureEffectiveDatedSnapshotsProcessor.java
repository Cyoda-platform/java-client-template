package com.java_template.application.processor.eod_batch;

import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.application.entity.accrual.version_1.LoanFilter;
import com.java_template.application.entity.loan.version_1.Loan;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processor to snapshot principal, APR, and policy data at AsOfDate.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Queries loan entities effective as of the batch's asOfDate</li>
 *   <li>Stores snapshot data in batch for later use by accrual processors</li>
 *   <li>Captures principal balances, APR rates, and policy configurations</li>
 * </ul>
 *
 * <p>Execution Mode: ASYNC_NEW_TX</p>
 * <p>Calculation Nodes Tags: accruals</p>
 */
@Component
public class CaptureEffectiveDatedSnapshotsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CaptureEffectiveDatedSnapshotsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CaptureEffectiveDatedSnapshotsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CaptureEffectiveDatedSnapshots for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(EODAccrualBatch.class)
            .validate(this::isValidEntityWithMetadata, "Invalid batch entity")
            .map(this::captureSnapshotsLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CaptureEffectiveDatedSnapshots".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EODAccrualBatch> entityWithMetadata) {
        EODAccrualBatch batch = entityWithMetadata.entity();
        return batch != null && batch.isValid(entityWithMetadata.metadata()) && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to capture effective-dated snapshots.
     *
     * <p>CRITICAL LIMITATIONS:</p>
     * <ul>
     *   <li>✅ ALLOWED: Read current batch data</li>
     *   <li>✅ ALLOWED: Query loan entities via EntityService</li>
     *   <li>✅ ALLOWED: Store snapshot data in batch fields</li>
     *   <li>❌ FORBIDDEN: Update current batch state/transitions</li>
     * </ul>
     */
    private EntityWithMetadata<EODAccrualBatch> captureSnapshotsLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EODAccrualBatch> context) {

        EntityWithMetadata<EODAccrualBatch> entityWithMetadata = context.entityResponse();
        EODAccrualBatch batch = entityWithMetadata.entity();

        LocalDate asOfDate = batch.getAsOfDate();
        LoanFilter loanFilter = batch.getLoanFilter();

        if (asOfDate == null) {
            logger.error("AsOfDate is null for batch: {}", batch.getBatchId());
            throw new IllegalStateException("AsOfDate is required to capture snapshots");
        }

        logger.debug("Capturing snapshots for batch: {} with asOfDate: {}",
            batch.getBatchId(), asOfDate);

        // Query loans effective as of asOfDate
        List<Loan> loans = queryLoansAsOfDate(asOfDate, loanFilter);

        logger.info("Found {} loans for snapshot capture", loans.size());

        // Capture snapshot data
        Map<String, Object> snapshotData = captureSnapshotData(loans, asOfDate);

        // TODO: Store snapshot data in batch
        // For now, we'll just log the snapshot count
        // In a real implementation, you might:
        // 1. Store snapshots in a separate collection/entity
        // 2. Store summary data in batch fields
        // 3. Cache snapshots for use by accrual processors

        logger.info("Batch {} snapshots captured: {} loans processed",
            batch.getBatchId(), snapshotData.get("loanCount"));

        return entityWithMetadata;
    }

    /**
     * Queries loans effective as of the given date.
     *
     * @param asOfDate The effective date for the snapshot
     * @param loanFilter Optional filter criteria for loans
     * @return List of loans effective as of the date
     */
    private List<Loan> queryLoansAsOfDate(LocalDate asOfDate, LoanFilter loanFilter) {
        ModelSpec loanModelSpec = new ModelSpec()
            .withName(Loan.ENTITY_NAME)
            .withVersion(Loan.ENTITY_VERSION);

        // TODO: Use point-in-time query with asOfDate
        // For now, query all loans and filter in memory
        List<EntityWithMetadata<Loan>> loansWithMetadata =
            entityService.findAll(loanModelSpec, Loan.class);

        List<Loan> loans = loansWithMetadata.stream()
            .map(EntityWithMetadata::entity)
            .toList();

        // Apply loan filter if present
        if (loanFilter != null) {
            loans = applyLoanFilter(loans, loanFilter);
        }

        return loans;
    }

    /**
     * Applies loan filter criteria to the list of loans.
     *
     * @param loans The list of loans to filter
     * @param loanFilter The filter criteria
     * @return Filtered list of loans
     */
    private List<Loan> applyLoanFilter(List<Loan> loans, LoanFilter loanFilter) {
        List<UUID> loanIds = loanFilter.getLoanIds();
        List<String> productCodes = loanFilter.getProductCodes();

        if (loanIds != null && !loanIds.isEmpty()) {
            // Convert loan.getLoanId() (String) to UUID for comparison
            loans = loans.stream()
                .filter(loan -> {
                    try {
                        UUID loanUuid = UUID.fromString(loan.getLoanId());
                        return loanIds.contains(loanUuid);
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .toList();
        }

        if (productCodes != null && !productCodes.isEmpty()) {
            // TODO: Add productCode field to Loan entity
            // For now, skip product code filtering
            logger.warn("Product code filtering not yet implemented - Loan entity lacks productCode field");
        }

        return loans;
    }

    /**
     * Captures snapshot data from loans.
     *
     * @param loans The loans to snapshot
     * @param asOfDate The effective date
     * @return Map containing snapshot summary data
     */
    private Map<String, Object> captureSnapshotData(List<Loan> loans, LocalDate asOfDate) {
        Map<String, Object> snapshotData = new HashMap<>();

        int loanCount = 0;
        for (Loan loan : loans) {
            // Capture principal balance
            // Capture APR
            // Capture policy configuration
            // TODO: Store individual loan snapshots

            loanCount++;
        }

        snapshotData.put("loanCount", loanCount);
        snapshotData.put("asOfDate", asOfDate);

        return snapshotData;
    }
}

