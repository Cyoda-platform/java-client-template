package com.java_template.application.criterion.eod_batch;

import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatchState;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Criterion to ensure only one active batch exists per AsOfDate.
 *
 * <p>This criterion validates that no other batch is currently processing
 * for the same asOfDate. A batch is considered active if it is in any
 * non-terminal state (not COMPLETED, FAILED, or CANCELED).</p>
 *
 * <p>This prevents concurrent batch runs for the same business date,
 * which could lead to duplicate accruals or data inconsistencies.</p>
 *
 * <p>This is a pure function with no side effects.</p>
 */
@Component
public class NoActiveBatchForDateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    private static final Set<EODAccrualBatchState> TERMINAL_STATES = Set.of(
        EODAccrualBatchState.COMPLETED,
        EODAccrualBatchState.FAILED,
        EODAccrualBatchState.CANCELED
    );

    public NoActiveBatchForDateCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking NoActiveBatchForDate criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateNoActiveBatch)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NoActiveBatchForDate".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that no other active batch exists for the same asOfDate.
     *
     * @param context The criterion evaluation context containing the batch
     * @return EvaluationOutcome.success() if no active batch exists, otherwise failure with reason
     */
    private EvaluationOutcome validateNoActiveBatch(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("EODAccrualBatch entity is null");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        LocalDate asOfDate = batch.getAsOfDate();

        // Check if asOfDate is null
        if (asOfDate == null) {
            logger.warn("AsOfDate is null for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("AsOfDate is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        try {
            // Query for all batches with the same asOfDate
            ModelSpec batchModelSpec = new ModelSpec()
                .withName(EODAccrualBatch.ENTITY_NAME)
                .withVersion(EODAccrualBatch.ENTITY_VERSION);

            List<EntityWithMetadata<EODAccrualBatch>> existingBatchesWithMetadata =
                entityService.findAll(batchModelSpec, EODAccrualBatch.class);

            // Filter for batches with same asOfDate and non-terminal states
            long activeBatchCount = existingBatchesWithMetadata.stream()
                .filter(b -> asOfDate.equals(b.entity().getAsOfDate()))
                .filter(b -> !batch.getBatchId().equals(b.entity().getBatchId())) // Exclude current batch
                .filter(b -> !TERMINAL_STATES.contains(EODAccrualBatchState.valueOf(b.getState())))
                .count();

            if (activeBatchCount > 0) {
                logger.warn("Found {} active batch(es) for asOfDate {} (excluding current batch {})",
                    activeBatchCount, asOfDate, batch.getBatchId());
                return EvaluationOutcome.fail(
                    String.format("Another active batch already exists for asOfDate %s", asOfDate),
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }

            logger.debug("No active batches found for asOfDate {} (batch: {})", asOfDate, batch.getBatchId());
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error querying for existing batches: {}", e.getMessage(), e);
            return EvaluationOutcome.fail(
                "Failed to check for existing batches: " + e.getMessage(),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }
    }
}

