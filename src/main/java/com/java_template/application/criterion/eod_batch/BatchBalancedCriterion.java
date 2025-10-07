package com.java_template.application.criterion.eod_batch;

import com.java_template.application.entity.accrual.version_1.BatchMetrics;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.serializer.*;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Criterion to verify that the batch is balanced with debits equal to credits.
 *
 * <p>This criterion checks the batch metrics to ensure that:</p>
 * <ul>
 *   <li>Total debited amount equals total credited amount</li>
 *   <li>No imbalances have been detected</li>
 * </ul>
 *
 * <p>This is a critical reconciliation check before completing the batch,
 * ensuring the integrity of the accounting entries.</p>
 *
 * <p>This is a pure function with no side effects.</p>
 */
@Component
public class BatchBalancedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public BatchBalancedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking BatchBalanced criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateBatchBalanced)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "BatchBalanced".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the batch is balanced.
     *
     * @param context The criterion evaluation context containing the batch
     * @return EvaluationOutcome.success() if batch is balanced, otherwise failure with reason
     */
    private EvaluationOutcome validateBatchBalanced(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("EODAccrualBatch entity is null");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        BatchMetrics metrics = batch.getMetrics();

        // Check if metrics is null
        if (metrics == null) {
            logger.warn("Metrics is null for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("Batch metrics are required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        BigDecimal debited = metrics.getDebited();
        BigDecimal credited = metrics.getCredited();
        int imbalances = metrics.getImbalances();

        // Check if debited or credited amounts are null
        if (debited == null || credited == null) {
            logger.warn("Debited or credited amount is null for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail(
                "Debited and credited amounts must be set",
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        // Check for imbalances
        if (imbalances > 0) {
            logger.warn("Batch {} has {} imbalances detected", batch.getBatchId(), imbalances);
            return EvaluationOutcome.fail(
                String.format("Batch has %d imbalances that must be resolved", imbalances),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        // Check if debits equal credits
        // Use compareTo for BigDecimal comparison (equals() checks scale too)
        if (debited.compareTo(credited) != 0) {
            BigDecimal difference = debited.subtract(credited);
            logger.warn("Batch {} is not balanced: debited={}, credited={}, difference={}",
                batch.getBatchId(), debited, credited, difference);
            return EvaluationOutcome.fail(
                String.format("Batch is not balanced: debited=%s, credited=%s, difference=%s",
                    debited, credited, difference),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        logger.info("Batch {} is balanced: debited={}, credited={}, imbalances=0",
            batch.getBatchId(), debited, credited);
        return EvaluationOutcome.success();
    }
}

