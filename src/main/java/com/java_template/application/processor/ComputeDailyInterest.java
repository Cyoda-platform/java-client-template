package com.java_template.application.processor;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;

/**
 * ABOUTME: This processor performs the daily interest calculation for active loans,
 * implementing the precise business rules for different day-count conventions.
 */
@Component
public class ComputeDailyInterest implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ComputeDailyInterest.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    // High precision for internal calculations (8 decimal places minimum)
    private static final MathContext CALCULATION_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final int RESULT_SCALE = 8;

    public ComputeDailyInterest(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Accrual.class)
                .validate(this::isValidEntityWithMetadata, "Invalid accrual entity wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Accrual> entityWithMetadata) {
        Accrual entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Accrual> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        logger.debug("Computing daily interest for loan: {} on date: {}", 
                    accrual.getLoanId(), accrual.getValueDate());

        // Validate required fields
        if (accrual.getPrincipalBase() == null || accrual.getPrincipalBase().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Principal base must be non-negative");
        }

        if (accrual.getEffectiveRate() == null || accrual.getEffectiveRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Effective rate must be positive");
        }

        if (accrual.getValueDate() == null) {
            throw new IllegalArgumentException("Value date is required");
        }

        // Calculate day count fraction based on convention
        BigDecimal dayCountFraction = calculateDayCountFraction(accrual.getDayCountBasis(), accrual.getValueDate());

        // Perform interest calculation: principalBase * effectiveRate * dcf
        BigDecimal interestAmount = accrual.getPrincipalBase()
                .multiply(accrual.getEffectiveRate(), CALCULATION_CONTEXT)
                .multiply(dayCountFraction, CALCULATION_CONTEXT)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);

        // Set the calculated amount
        accrual.setAccruedAmount(interestAmount);

        // Set calculation details
        if (accrual.getCalculation() == null) {
            accrual.setCalculation(new Accrual.AccrualCalculation());
        }
        
        accrual.getCalculation().setFormula("principalBase * effectiveRate * dcf");
        accrual.getCalculation().setDayCountFraction(dayCountFraction);

        logger.info("Interest calculated for loan {}: Principal={}, Rate={}, DCF={}, Interest={}", 
                   accrual.getLoanId(), accrual.getPrincipalBase(), accrual.getEffectiveRate(), 
                   dayCountFraction, interestAmount);

        return entityWithMetadata;
    }

    private BigDecimal calculateDayCountFraction(String dayCountBasis, LocalDate valueDate) {
        if (dayCountBasis == null) {
            dayCountBasis = "ACT/365"; // Default
        }

        switch (dayCountBasis.toUpperCase()) {
            case "ACT/365":
                return calculateActual365(valueDate);
            case "ACT/360":
                return calculateActual360();
            case "30/360":
                return calculate30360();
            default:
                logger.warn("Unknown day count basis: {}, defaulting to ACT/365", dayCountBasis);
                return calculateActual365(valueDate);
        }
    }

    private BigDecimal calculateActual365(LocalDate valueDate) {
        // Check if it's a leap year
        boolean isLeapYear = Year.of(valueDate.getYear()).isLeap();
        int daysInYear = isLeapYear ? 366 : 365;
        
        BigDecimal fraction = BigDecimal.ONE.divide(BigDecimal.valueOf(daysInYear), CALCULATION_CONTEXT);
        
        logger.debug("ACT/365 calculation: 1/{} = {}", daysInYear, fraction);
        return fraction;
    }

    private BigDecimal calculateActual360() {
        BigDecimal fraction = BigDecimal.ONE.divide(BigDecimal.valueOf(360), CALCULATION_CONTEXT);
        logger.debug("ACT/360 calculation: 1/360 = {}", fraction);
        return fraction;
    }

    private BigDecimal calculate30360() {
        BigDecimal fraction = BigDecimal.ONE.divide(BigDecimal.valueOf(360), CALCULATION_CONTEXT);
        logger.debug("30/360 calculation: 1/360 = {}", fraction);
        return fraction;
    }
}
