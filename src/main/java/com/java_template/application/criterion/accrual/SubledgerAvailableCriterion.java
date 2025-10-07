package com.java_template.application.criterion.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.common.serializer.*;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Criterion to verify that the sub-ledger is available and properly configured.
 *
 * This criterion checks:
 * - Sub-ledger service is reachable
 * - Required GL accounts are configured (INTEREST_RECEIVABLE, INTEREST_INCOME)
 * - Currency is supported in the sub-ledger
 *
 * In a production system, this would make actual calls to the sub-ledger service.
 * For this implementation, we perform basic validation checks.
 *
 * This is a pure function with no side effects.
 */
@Component
public class SubledgerAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubledgerAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking SubledgerAvailable criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateSubledgerAvailable)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubledgerAvailable".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the sub-ledger is available for posting journal entries.
     *
     * @param context The criterion evaluation context containing the accrual
     * @return EvaluationOutcome.success() if sub-ledger is available, otherwise failure with reason
     */
    private EvaluationOutcome validateSubledgerAvailable(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        Accrual accrual = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (accrual == null) {
            logger.warn("Accrual entity is null");
            return EvaluationOutcome.fail("Accrual entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        String currency = accrual.getCurrency();

        // Check required fields
        if (currency == null || currency.trim().isEmpty()) {
            logger.warn("Currency is null or empty for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("Currency is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // TODO: In production, this would:
        // 1. Check if sub-ledger service is reachable (health check)
        // 2. Verify GL accounts INTEREST_RECEIVABLE and INTEREST_INCOME exist
        // 3. Verify currency is supported in the sub-ledger
        // 4. Check if posting is allowed for the current date/time

        // For now, we perform basic validation
        // Assume sub-ledger is available if we have a valid currency
        // In a real implementation, you would inject a SubledgerService and call it here

        logger.debug("Sub-ledger validation passed for accrual {} with currency {}",
            accrual.getAccrualId(), currency);

        // Simulate a basic check - in production this would be an actual service call
        if (!isSubledgerHealthy()) {
            logger.warn("Sub-ledger service is not healthy for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail(
                "Sub-ledger service is not available",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        if (!areAccountsConfigured(currency)) {
            logger.warn("GL accounts not configured for currency {} in accrual: {}", currency, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("GL accounts not configured for currency %s", currency),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("Sub-ledger is available for accrual: {}", accrual.getAccrualId());
        return EvaluationOutcome.success();
    }

    /**
     * Simulates a health check for the sub-ledger service.
     * In production, this would make an actual HTTP/gRPC call to the sub-ledger.
     *
     * @return true if sub-ledger is healthy, false otherwise
     */
    private boolean isSubledgerHealthy() {
        // TODO: Replace with actual sub-ledger health check
        // For now, always return true (assume healthy)
        return true;
    }

    /**
     * Checks if the required GL accounts are configured for the given currency.
     * In production, this would query the sub-ledger configuration.
     *
     * @param currency The currency to check
     * @return true if accounts are configured, false otherwise
     */
    private boolean areAccountsConfigured(String currency) {
        // TODO: Replace with actual sub-ledger account configuration check
        // For now, assume all standard currencies (USD, EUR, GBP) are configured
        return currency.matches("USD|EUR|GBP|JPY|CHF|CAD|AUD");
    }
}

