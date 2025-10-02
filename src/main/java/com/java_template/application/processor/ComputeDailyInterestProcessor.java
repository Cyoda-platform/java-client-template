package com.java_template.application.processor;

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
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ABOUTME: This processor computes daily interest accruals using the loan's principal balance,
 * APR, and day count basis with high precision calculations.
 */
@Component
public class ComputeDailyInterestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ComputeDailyInterestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // High precision math context for interest calculations
    private static final MathContext PRECISION = new MathContext(12, RoundingMode.HALF_UP);

    public ComputeDailyInterestProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Accrual.class)
                .validate(this::isValidEntityWithMetadata, "Invalid accrual entity wrapper")
                .map(this::computeInterestLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Accrual> entityWithMetadata) {
        Accrual accrual = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return accrual != null && accrual.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Accrual> computeInterestLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        logger.debug("Computing interest for accrual: {} on date: {}", accrual.getAccrualId(), accrual.getAccrualDate());

        // Get the associated loan to get current principal balance
        Loan loan = getLoanForAccrual(accrual);
        
        // Set principal base from loan's current outstanding principal
        accrual.setPrincipalBase(loan.getOutstandingPrincipal());
        accrual.setApr(loan.getApr());
        accrual.setDayCountBasis(loan.getDayCountBasis());

        // Calculate day count factors
        calculateDayCountFactors(accrual);

        // Compute daily interest
        BigDecimal interestAmount = calculateDailyInterest(accrual);
        accrual.setInterestAmount(interestAmount);

        // Calculate and store daily rate for reference
        BigDecimal dailyRate = calculateDailyRate(accrual.getApr(), accrual.getDayCountDenominator());
        accrual.setDailyRate(dailyRate);

        // Set calculation timestamp
        accrual.setCalculatedAt(LocalDateTime.now());
        accrual.setUpdatedAt(LocalDateTime.now());

        logger.info("Interest computed for accrual: {}. Principal: {}, Interest: {}, Rate: {}", 
                   accrual.getAccrualId(), 
                   accrual.getPrincipalBase(),
                   interestAmount,
                   dailyRate);

        return entityWithMetadata;
    }

    private Loan getLoanForAccrual(Accrual accrual) {
        try {
            ModelSpec loanModelSpec = new ModelSpec()
                    .withName(Loan.ENTITY_NAME)
                    .withVersion(Loan.ENTITY_VERSION);
            
            EntityWithMetadata<Loan> loanResponse = entityService.findByBusinessId(
                    loanModelSpec, accrual.getLoanId(), "loanId", Loan.class);

            if (loanResponse == null) {
                throw new IllegalArgumentException("Loan not found: " + accrual.getLoanId());
            }

            return loanResponse.entity();
        } catch (Exception e) {
            logger.error("Failed to retrieve loan for accrual: {}", accrual.getLoanId(), e);
            throw new IllegalArgumentException("Cannot compute accrual - loan not found: " + accrual.getLoanId(), e);
        }
    }

    private void calculateDayCountFactors(Accrual accrual) {
        String dayCountBasis = accrual.getDayCountBasis();
        LocalDate accrualDate = accrual.getAccrualDate();
        
        // For daily accruals, numerator is always 1
        accrual.setDayCountNumerator(1);
        
        // Set denominator based on day count convention
        switch (dayCountBasis) {
            case "ACT/365F":
                accrual.setDayCountDenominator(365);
                break;
            case "ACT/360":
                accrual.setDayCountDenominator(360);
                break;
            case "ACT/365L":
                // Leap year sensitive
                int year = accrualDate.getYear();
                boolean isLeapYear = LocalDate.of(year, 1, 1).isLeapYear();
                accrual.setDayCountDenominator(isLeapYear ? 366 : 365);
                break;
            default:
                throw new IllegalArgumentException("Unsupported day count basis: " + dayCountBasis);
        }
    }

    private BigDecimal calculateDailyInterest(Accrual accrual) {
        BigDecimal principal = accrual.getPrincipalBase();
        BigDecimal apr = accrual.getApr();
        Integer denominator = accrual.getDayCountDenominator();
        
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Daily Interest = Principal × (APR / 100) × (1 / Denominator)
        BigDecimal aprDecimal = apr.divide(new BigDecimal("100"), PRECISION);
        BigDecimal dailyRate = aprDecimal.divide(new BigDecimal(denominator), PRECISION);
        BigDecimal dailyInterest = principal.multiply(dailyRate, PRECISION);
        
        // Round to 8 decimal places for storage
        return dailyInterest.setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDailyRate(BigDecimal apr, Integer denominator) {
        BigDecimal aprDecimal = apr.divide(new BigDecimal("100"), PRECISION);
        return aprDecimal.divide(new BigDecimal(denominator), PRECISION);
    }
}
