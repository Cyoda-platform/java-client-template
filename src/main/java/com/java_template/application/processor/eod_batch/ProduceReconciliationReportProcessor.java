package com.java_template.application.processor.eod_batch;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.BatchMetrics;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.application.entity.accrual.version_1.JournalEntry;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processor to generate reconciliation summaries and PPA reports.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Aggregates all journal entries from accruals with matching runId</li>
 *   <li>Calculates total debits, credits, and identifies imbalances</li>
 *   <li>Identifies all accruals with priorPeriodFlag=true for PPA section</li>
 *   <li>Generates report file (CSV or JSON format)</li>
 *   <li>Stores report file reference in batch's reportId field</li>
 *   <li>Updates batch metrics with final totals</li>
 * </ul>
 *
 * <p>Execution Mode: ASYNC_NEW_TX</p>
 * <p>Calculation Nodes Tags: ledger</p>
 */
@Component
public class ProduceReconciliationReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProduceReconciliationReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProduceReconciliationReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ProduceReconciliationReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(EODAccrualBatch.class)
            .validate(this::isValidEntityWithMetadata, "Invalid batch entity")
            .map(this::produceReportLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ProduceReconciliationReport".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EODAccrualBatch> entityWithMetadata) {
        EODAccrualBatch batch = entityWithMetadata.entity();
        return batch != null && batch.isValid(entityWithMetadata.metadata()) && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to produce reconciliation report.
     *
     * <p>CRITICAL LIMITATIONS:</p>
     * <ul>
     *   <li>✅ ALLOWED: Read current batch data</li>
     *   <li>✅ ALLOWED: Query Accrual entities via EntityService</li>
     *   <li>✅ ALLOWED: Update batch metrics and reportId</li>
     *   <li>❌ FORBIDDEN: Update current batch state/transitions</li>
     * </ul>
     */
    private EntityWithMetadata<EODAccrualBatch> produceReportLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EODAccrualBatch> context) {

        EntityWithMetadata<EODAccrualBatch> entityWithMetadata = context.entityResponse();
        EODAccrualBatch batch = entityWithMetadata.entity();

        UUID batchId = batch.getBatchId();

        logger.debug("Producing reconciliation report for batch: {}", batchId);

        // Query all accruals for this batch
        List<Accrual> batchAccruals = queryBatchAccruals(batchId);

        logger.info("Found {} accruals for batch: {}", batchAccruals.size(), batchId);

        // Aggregate journal entries
        ReconciliationData reconData = aggregateJournalEntries(batchAccruals);

        // Generate report
        UUID reportId = generateReport(batchId, reconData);

        // Update batch metrics
        updateBatchMetrics(batch, reconData);

        // Store report reference
        batch.setReportId(reportId);

        logger.info("Batch {} reconciliation report generated: reportId={}, debits={}, credits={}, imbalances={}",
            batchId, reportId, reconData.totalDebits, reconData.totalCredits, reconData.imbalances);

        return entityWithMetadata;
    }

    /**
     * Queries all accruals for the given batch.
     */
    private List<Accrual> queryBatchAccruals(UUID runId) {
        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        List<EntityWithMetadata<Accrual>> accrualsWithMetadata =
            entityService.findAll(accrualModelSpec, Accrual.class);

        // Filter for accruals with matching runId
        List<Accrual> batchAccruals = new ArrayList<>();
        for (EntityWithMetadata<Accrual> accrualWithMetadata : accrualsWithMetadata) {
            Accrual accrual = accrualWithMetadata.entity();
            if (runId.toString().equals(accrual.getRunId())) {
                batchAccruals.add(accrual);
            }
        }

        return batchAccruals;
    }

    /**
     * Aggregates journal entries from all accruals.
     */
    private ReconciliationData aggregateJournalEntries(List<Accrual> accruals) {
        ReconciliationData data = new ReconciliationData();

        for (Accrual accrual : accruals) {
            List<JournalEntry> entries = accrual.getJournalEntries();
            if (entries == null || entries.isEmpty()) {
                continue;
            }

            for (JournalEntry entry : entries) {
                BigDecimal amount = entry.getAmount();
                if (amount == null) {
                    continue;
                }

                if ("DR".equals(entry.getDirection())) {
                    data.totalDebits = data.totalDebits.add(amount);
                } else if ("CR".equals(entry.getDirection())) {
                    data.totalCredits = data.totalCredits.add(amount);
                }
            }

            // Track prior period accruals
            if (accrual.getPriorPeriodFlag() != null && accrual.getPriorPeriodFlag()) {
                data.priorPeriodAccruals.add(accrual);
            }
        }

        // Check for imbalances
        if (data.totalDebits.compareTo(data.totalCredits) != 0) {
            data.imbalances = 1;
        }

        return data;
    }

    /**
     * Generates the reconciliation report file.
     *
     * <p>TODO: Implement actual report generation and file storage.</p>
     * <p>This could involve:</p>
     * <ul>
     *   <li>Generating CSV or JSON report file</li>
     *   <li>Storing file in S3, file system, or document store</li>
     *   <li>Returning file reference/URL</li>
     * </ul>
     *
     * @param batchId The batch ID
     * @param data The reconciliation data
     * @return Report ID or file reference
     */
    private UUID generateReport(UUID batchId, ReconciliationData data) {
        // TODO: Generate actual report file
        // For now, just create a report ID
        UUID reportId = UUID.randomUUID();

        logger.info("Generated report: {}", reportId);
        logger.info("  Total Debits: {}", data.totalDebits);
        logger.info("  Total Credits: {}", data.totalCredits);
        logger.info("  Imbalances: {}", data.imbalances);
        logger.info("  Prior Period Accruals: {}", data.priorPeriodAccruals.size());

        return reportId;
    }

    /**
     * Updates batch metrics with reconciliation totals.
     */
    private void updateBatchMetrics(EODAccrualBatch batch, ReconciliationData data) {
        BatchMetrics metrics = batch.getMetrics();
        if (metrics == null) {
            metrics = new BatchMetrics();
            batch.setMetrics(metrics);
        }

        metrics.setDebited(data.totalDebits);
        metrics.setCredited(data.totalCredits);
        metrics.setImbalances(data.imbalances);
    }

    /**
     * Internal class to hold reconciliation data.
     */
    private static class ReconciliationData {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        int imbalances = 0;
        List<Accrual> priorPeriodAccruals = new ArrayList<>();
    }
}

