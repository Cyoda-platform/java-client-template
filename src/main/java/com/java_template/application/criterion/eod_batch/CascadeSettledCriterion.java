package com.java_template.application.criterion.eod_batch;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.AccrualState;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
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
import java.util.UUID;

/**
 * Criterion to verify that all cascade recalculations are finished.
 *
 * <p>For back-dated batch runs, this criterion checks that all cascade
 * recalculations triggered by the historical correction have completed.
 * Cascade recalculations update accruals for dates after the back-dated
 * asOfDate to reflect the impact of the correction.</p>
 *
 * <p>This criterion queries for any accruals in the cascade date range
 * (from cascadeFromDate onwards) that are still in non-terminal states.</p>
 *
 * <p>This is a pure function with no side effects.</p>
 */
@Component
public class CascadeSettledCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    private static final Set<AccrualState> TERMINAL_STATES = Set.of(
        AccrualState.POSTED,
        AccrualState.FAILED,
        AccrualState.CANCELED,
        AccrualState.SUPERSEDED
    );

    public CascadeSettledCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CascadeSettled criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateCascadeSettled)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CascadeSettled".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that all cascade recalculations are complete.
     *
     * @param context The criterion evaluation context containing the batch
     * @return EvaluationOutcome.success() if all cascades are settled, otherwise failure with reason
     */
    private EvaluationOutcome validateCascadeSettled(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("EODAccrualBatch entity is null");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        UUID batchId = batch.getBatchId();
        LocalDate cascadeFromDate = batch.getCascadeFromDate();

        // Check if batchId is null
        if (batchId == null) {
            logger.warn("BatchId is null for batch");
            return EvaluationOutcome.fail("Batch ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // If cascadeFromDate is null, no cascade was triggered (TODAY mode)
        if (cascadeFromDate == null) {
            logger.debug("No cascade date set for batch {} - assuming no cascade required", batchId);
            return EvaluationOutcome.success();
        }

        try {
            // Query for all accruals
            ModelSpec accrualModelSpec = new ModelSpec()
                .withName(Accrual.ENTITY_NAME)
                .withVersion(Accrual.ENTITY_VERSION);

            List<EntityWithMetadata<Accrual>> allAccrualsWithMetadata =
                entityService.findAll(accrualModelSpec, Accrual.class);

            // Filter for accruals in the cascade date range that are related to this batch
            // TODO: In production, this should be optimized with a more specific query
            // For now, we check accruals with asOfDate >= cascadeFromDate
            List<EntityWithMetadata<Accrual>> cascadeAccruals = allAccrualsWithMetadata.stream()
                .filter(a -> a.entity().getAsOfDate() != null)
                .filter(a -> !a.entity().getAsOfDate().isBefore(cascadeFromDate))
                .filter(a -> batchId.toString().equals(a.entity().getRunId()) || isRelatedToCascade(a.entity(), batchId))
                .toList();

            if (cascadeAccruals.isEmpty()) {
                // No cascade accruals found - may still be spawning
                logger.debug("No cascade accruals found for batch {} from date {}", batchId, cascadeFromDate);
                return EvaluationOutcome.fail(
                    "Cascade recalculations have not been initiated yet",
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }

            // Check if all cascade accruals are in terminal states
            long totalCascadeAccruals = cascadeAccruals.size();
            long settledCascadeAccruals = cascadeAccruals.stream()
                .filter(a -> TERMINAL_STATES.contains(AccrualState.valueOf(a.getState())))
                .count();

            if (settledCascadeAccruals < totalCascadeAccruals) {
                long pendingCascadeAccruals = totalCascadeAccruals - settledCascadeAccruals;
                logger.debug("Batch {} has {} pending cascade accruals out of {} total",
                    batchId, pendingCascadeAccruals, totalCascadeAccruals);
                return EvaluationOutcome.fail(
                    String.format("%d of %d cascade accruals are still processing",
                        pendingCascadeAccruals, totalCascadeAccruals),
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }

            logger.info("All {} cascade accruals for batch {} are settled", totalCascadeAccruals, batchId);
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error querying for cascade accruals: {}", e.getMessage(), e);
            return EvaluationOutcome.fail(
                "Failed to check cascade status: " + e.getMessage(),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }
    }

    /**
     * Check if an accrual is related to the cascade operation for this batch.
     *
     * TODO: In production, this should use a more robust mechanism to track
     * cascade relationships (e.g., a cascade tracking entity or metadata field).
     *
     * @param accrual The accrual to check
     * @param batchId The batch ID
     * @return true if the accrual is part of the cascade, false otherwise
     */
    private boolean isRelatedToCascade(Accrual accrual, UUID batchId) {
        // TODO: Implement proper cascade relationship tracking
        // For now, we use a simple heuristic based on runId
        return batchId.toString().equals(accrual.getRunId());
    }
}

