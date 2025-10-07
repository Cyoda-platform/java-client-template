package com.java_template.application.processor.eod_batch;

import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.application.entity.accrual.version_1.PeriodStatus;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Processor to determine GL period status (OPEN/CLOSED) for the batch's AsOfDate.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Queries GL calendar or period configuration to determine if period is closed</li>
 *   <li>Updates batch's periodStatus field (OPEN or CLOSED)</li>
 *   <li>Sets priorPeriodFlag logic based on period status</li>
 * </ul>
 *
 * <p>Execution Mode: SYNC</p>
 */
@Component
public class ResolvePeriodStatusProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ResolvePeriodStatusProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ResolvePeriodStatusProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ResolvePeriodStatus for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(EODAccrualBatch.class)
            .validate(this::isValidEntityWithMetadata, "Invalid batch entity")
            .map(this::resolvePeriodStatusLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ResolvePeriodStatus".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EODAccrualBatch> entityWithMetadata) {
        EODAccrualBatch batch = entityWithMetadata.entity();
        return batch != null && batch.isValid(entityWithMetadata.metadata()) && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to resolve period status.
     *
     * <p>CRITICAL LIMITATIONS:</p>
     * <ul>
     *   <li>✅ ALLOWED: Read current batch data</li>
     *   <li>✅ ALLOWED: Modify current batch fields (periodStatus, priorPeriodFlag)</li>
     *   <li>❌ FORBIDDEN: Update current batch state/transitions</li>
     * </ul>
     */
    private EntityWithMetadata<EODAccrualBatch> resolvePeriodStatusLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EODAccrualBatch> context) {

        EntityWithMetadata<EODAccrualBatch> entityWithMetadata = context.entityResponse();
        EODAccrualBatch batch = entityWithMetadata.entity();

        LocalDate asOfDate = batch.getAsOfDate();
        if (asOfDate == null) {
            logger.error("AsOfDate is null for batch: {}", batch.getBatchId());
            throw new IllegalStateException("AsOfDate is required to resolve period status");
        }

        logger.debug("Resolving period status for batch: {} with asOfDate: {}",
            batch.getBatchId(), asOfDate);

        // Determine period status
        PeriodStatus periodStatus = determinePeriodStatus(asOfDate);
        batch.setPeriodStatus(periodStatus);

        // Note: priorPeriodFlag is set on individual accruals, not on the batch
        // The batch's periodStatus is used by processors to determine the flag for accruals

        logger.info("Batch {} period status resolved: periodStatus={}",
            batch.getBatchId(), periodStatus);

        return entityWithMetadata;
    }

    /**
     * Determines if the GL period for the given date is OPEN or CLOSED.
     *
     * <p>TODO: Integrate with actual GL calendar service or period configuration.</p>
     * <p>Current implementation uses a simple rule:</p>
     * <ul>
     *   <li>If asOfDate is in the current month: OPEN</li>
     *   <li>If asOfDate is in a prior month: CLOSED</li>
     * </ul>
     *
     * @param asOfDate The date to check
     * @return PeriodStatus.OPEN or PeriodStatus.CLOSED
     */
    private PeriodStatus determinePeriodStatus(LocalDate asOfDate) {
        // Get current month
        YearMonth currentMonth = YearMonth.now();
        YearMonth asOfMonth = YearMonth.from(asOfDate);

        // Simple rule: current month is OPEN, prior months are CLOSED
        if (asOfMonth.isBefore(currentMonth)) {
            logger.debug("AsOfDate {} is in prior month {}, period is CLOSED", asOfDate, asOfMonth);
            return PeriodStatus.CLOSED;
        } else {
            logger.debug("AsOfDate {} is in current month {}, period is OPEN", asOfDate, asOfMonth);
            return PeriodStatus.OPEN;
        }

        // TODO: Replace with actual GL calendar service integration
        // Example:
        // GLPeriod period = glCalendarService.getPeriodForDate(asOfDate);
        // return period.isClosed() ? PeriodStatus.CLOSED : PeriodStatus.OPEN;
    }
}

