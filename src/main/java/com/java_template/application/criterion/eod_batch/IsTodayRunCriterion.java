package com.java_template.application.criterion.eod_batch;

import com.java_template.application.entity.accrual.version_1.BatchMode;
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

import java.time.LocalDate;

/**
 * Criterion to determine if the batch is a normal today run.
 *
 * <p>This criterion checks if the batch mode is TODAY, which indicates
 * a normal end-of-day run for the current business date. TODAY runs
 * do not require cascade recalculations.</p>
 *
 * <p>This criterion is used to branch the workflow orchestration flow,
 * allowing TODAY batches to skip the cascade recalculation path.</p>
 *
 * <p>This is a pure function with no side effects.</p>
 */
@Component
public class IsTodayRunCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsTodayRunCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsTodayRun criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateIsToday)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsTodayRun".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the batch is a normal today run.
     *
     * @param context The criterion evaluation context containing the batch
     * @return EvaluationOutcome.success() if TODAY mode, otherwise failure
     */
    private EvaluationOutcome validateIsToday(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("EODAccrualBatch entity is null");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        BatchMode mode = batch.getMode();
        LocalDate asOfDate = batch.getAsOfDate();

        // Check if mode is null
        if (mode == null) {
            logger.warn("Mode is null for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("Batch mode is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if this is a TODAY run
        if (mode == BatchMode.TODAY) {
            logger.debug("Batch {} is a TODAY run for asOfDate: {}", batch.getBatchId(), asOfDate);
            return EvaluationOutcome.success();
        }

        // Not a today run
        logger.debug("Batch {} is not a TODAY run (mode: {})", batch.getBatchId(), mode);
        return EvaluationOutcome.fail(
            String.format("Batch mode is %s, not TODAY", mode),
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

