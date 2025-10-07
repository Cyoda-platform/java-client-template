package com.java_template.application.processor.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Processor to calculate the accrual interest amount.
 *
 * Formula: interestAmount = principal × APR × dayCountFraction
 *
 * This processor runs in ASYNC_NEW_TX mode on calculation nodes tagged "accruals".
 * It requires that DeriveDayCountFractionProcessor has already run to populate dayCountFraction.
 *
 * The calculated interest amount is used to generate journal entries in the next step.
 */
@Component
public class CalculateAccrualAmountProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CalculateAccrualAmountProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // Precision for monetary calculations (2 decimal places for most currencies)
    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode MONETARY_ROUNDING = RoundingMode.HALF_UP;

    public CalculateAccrualAmountProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CalculateAccrualAmount for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .validate(this::isValidEntityWithMetadata, "Invalid accrual entity")
            .map(this::calculateInterestAmount)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CalculateAccrualAmount".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Accrual> entityWithMetadata) {
        Accrual entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Calculates the interest amount using the formula:
     * interestAmount = principal × APR × dayCountFraction
     */
    private EntityWithMetadata<Accrual> calculateInterestAmount(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        logger.debug("Calculating interest amount for accrual: {}", accrual.getAccrualId());

        // Get required fields
        BigDecimal principal = accrual.getPrincipalSnapshot() != null ?
            accrual.getPrincipalSnapshot().getAmount() : null;
        BigDecimal dayCountFraction = accrual.getDayCountFraction();
        String loanId = accrual.getLoanId();

        // Validate required fields
        if (principal == null) {
            logger.error("Principal is null for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("Principal is required for interest calculation");
        }

        if (loanId == null || loanId.trim().isEmpty()) {
            logger.error("LoanId is null or empty for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("LoanId is required to retrieve APR");
        }

        if (dayCountFraction == null) {
            logger.error("DayCountFraction is null for accrual: {}. " +
                "DeriveDayCountFractionProcessor must run before this processor.",
                accrual.getAccrualId());
            throw new IllegalStateException("DayCountFraction is required for interest calculation. " +
                "Ensure DeriveDayCountFractionProcessor has run.");
        }

        // Retrieve APR from the loan entity
        BigDecimal apr = retrieveAprFromLoan(loanId, accrual.getAccrualId());

        // Calculate interest amount: principal × APR × dayCountFraction
        BigDecimal interestAmount = principal
            .multiply(apr)
            .multiply(dayCountFraction)
            .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

        // Set the calculated amount on the accrual
        accrual.setInterestAmount(interestAmount);

        logger.info("Interest amount calculated for accrual {}: {} (principal: {}, APR: {}, fraction: {})",
            accrual.getAccrualId(), interestAmount, principal, apr, dayCountFraction);

        // Log warning if interest amount is zero or negative
        if (interestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Interest amount is zero or negative for accrual {}: {}",
                accrual.getAccrualId(), interestAmount);
        }

        return entityWithMetadata;
    }

    /**
     * Retrieves the APR from the loan entity.
     */
    private BigDecimal retrieveAprFromLoan(String loanId, String accrualId) {
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
            logger.error("Error retrieving loan {} for accrual {}: {}",
                loanId, accrualId, e.getMessage());
            throw new IllegalStateException("Failed to retrieve loan: " + loanId, e);
        }

        if (loanWithMetadata == null) {
            logger.error("Loan {} not found for accrual: {}", loanId, accrualId);
            throw new IllegalStateException("Loan not found: " + loanId);
        }

        Loan loan = loanWithMetadata.entity();
        BigDecimal apr = loan.getApr();

        if (apr == null) {
            logger.error("APR is null for loan {} (accrual: {})", loanId, accrualId);
            throw new IllegalStateException("APR is required for interest calculation");
        }

        logger.debug("Retrieved APR {} from loan {} for accrual {}", apr, loanId, accrualId);
        return apr;
    }
}

