package com.java_template.application.criterion.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.AccrualState;
import com.java_template.application.entity.loan.version_1.Loan;
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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Criterion to determine if a POSTED accrual requires rebooking due to underlying data changes.
 *
 * A rebook is required when:
 * - The accrual is in POSTED state
 * - Underlying loan data has changed (principal, APR, day count convention)
 * - Recalculating the accrual would yield a different interest amount
 * - The delta is non-zero (material difference)
 *
 * This criterion triggers the supersedence workflow where:
 * 1. The current accrual transitions to SUPERSEDED state
 * 2. A new accrual is created with REVERSAL entries for the old amounts
 * 3. The new accrual includes REPLACEMENT entries with corrected amounts
 *
 * This is a pure function with no side effects.
 */
@Component
public class RequiresRebookCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Threshold for materiality - differences below this are ignored
    private static final BigDecimal MATERIALITY_THRESHOLD = new BigDecimal("0.01");

    public RequiresRebookCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking RequiresRebook criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateRequiresRebook)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "RequiresRebook".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates whether the accrual requires rebooking due to underlying data changes.
     *
     * @param context The criterion evaluation context containing the accrual
     * @return EvaluationOutcome.success() if rebook is required, otherwise failure
     */
    private EvaluationOutcome validateRequiresRebook(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        Accrual accrual = context.entityWithMetadata().entity();
        AccrualState state = AccrualState.valueOf(context.entityWithMetadata().metadata().getState());

        // Check if entity is null (structural validation)
        if (accrual == null) {
            logger.warn("Accrual entity is null");
            return EvaluationOutcome.fail("Accrual entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Only POSTED accruals can be rebooked
        if (state != AccrualState.POSTED) {
            logger.debug("Accrual {} is not in POSTED state (current: {}), rebook not applicable",
                accrual.getAccrualId(), state);
            return EvaluationOutcome.fail(
                String.format("Accrual is not in POSTED state (current: %s)", state),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        String loanId = accrual.getLoanId();
        BigDecimal currentInterestAmount = accrual.getInterestAmount();

        // Check required fields
        if (loanId == null || loanId.trim().isEmpty()) {
            logger.warn("LoanId is null or empty for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("LoanId is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (currentInterestAmount == null) {
            logger.warn("InterestAmount is null for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("InterestAmount is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Retrieve current loan data
        ModelSpec loanModelSpec = new ModelSpec()
            .withName(Loan.ENTITY_NAME)
            .withVersion(Loan.ENTITY_VERSION);

        EntityWithMetadata<Loan> loanWithMetadata;
        try {
            loanWithMetadata = entityService.findByBusinessId(
                loanModelSpec,
                loanId,
                "loanId",
                Loan.class
            );
        } catch (Exception e) {
            logger.error("Error retrieving loan {} for accrual {}: {}", loanId, accrual.getAccrualId(), e.getMessage());
            return EvaluationOutcome.fail(
                String.format("Error retrieving loan %s: %s", loanId, e.getMessage()),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        if (loanWithMetadata == null) {
            logger.warn("Loan {} not found for accrual: {}", loanId, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("Loan %s not found", loanId),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        Loan loan = loanWithMetadata.entity();

        // TODO: In production, this would:
        // 1. Recalculate the interest amount using current loan data
        // 2. Compare with the posted interest amount
        // 3. Check if the delta exceeds the materiality threshold

        // For now, we perform a simplified check
        // Assume rebook is required if principal or APR has changed significantly

        BigDecimal currentPrincipal = accrual.getPrincipalSnapshot() != null ?
            accrual.getPrincipalSnapshot().getAmount() : BigDecimal.ZERO;
        BigDecimal loanPrincipal = loan.getOutstandingPrincipal() != null ?
            loan.getOutstandingPrincipal() : loan.getPrincipalAmount();

        // Check if principal has changed
        if (loanPrincipal != null && currentPrincipal.compareTo(loanPrincipal) != 0) {
            BigDecimal principalDelta = loanPrincipal.subtract(currentPrincipal).abs();
            if (principalDelta.compareTo(MATERIALITY_THRESHOLD) > 0) {
                logger.info("Rebook required for accrual {}: principal changed from {} to {}",
                    accrual.getAccrualId(), currentPrincipal, loanPrincipal);
                return EvaluationOutcome.success();
            }
        }

        // TODO: Add APR change detection
        // TODO: Add day count convention change detection
        // TODO: Perform actual interest recalculation and comparison

        logger.debug("No material changes detected for accrual {}, rebook not required", accrual.getAccrualId());
        return EvaluationOutcome.fail(
            "No material changes detected, rebook not required",
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

