package com.java_template.application.processor.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.DayCountConvention;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Processor to compute day-count fraction per product convention.
 * 
 * Supports three day count conventions:
 * - ACT_360: Actual days / 360
 * - ACT_365: Actual days / 365
 * - THIRTY_360: 30/360 (assumes 30 days per month, 360 days per year)
 * 
 * The day count fraction is used in interest calculation:
 * interestAmount = principal × APR × dayCountFraction
 * 
 * This processor runs in SYNC mode and updates the accrual entity with the calculated fraction.
 */
@Component
public class DeriveDayCountFractionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeriveDayCountFractionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeriveDayCountFractionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DeriveDayCountFraction for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .validate(this::isValidEntityWithMetadata, "Invalid accrual entity")
            .map(this::calculateDayCountFraction)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DeriveDayCountFraction".equalsIgnoreCase(modelSpec.operationName());
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
     * Calculates the day count fraction based on the accrual's day count convention.
     */
    private EntityWithMetadata<Accrual> calculateDayCountFraction(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        logger.debug("Calculating day count fraction for accrual: {}", accrual.getAccrualId());

        // Get required fields
        LocalDate asOfDate = accrual.getAsOfDate();
        DayCountConvention convention = accrual.getDayCountConvention();

        if (asOfDate == null) {
            logger.error("AsOfDate is null for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("AsOfDate is required for day count calculation");
        }

        if (convention == null) {
            logger.error("DayCountConvention is null for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("DayCountConvention is required for day count calculation");
        }

        // Calculate the previous business day (for daily accrual, this is typically asOfDate - 1)
        // In a production system, this would use a business calendar service
        LocalDate previousDate = asOfDate.minusDays(1);

        // Calculate day count fraction based on convention
        BigDecimal dayCountFraction = switch (convention) {
            case ACT_360 -> calculateActual360(previousDate, asOfDate);
            case ACT_365 -> calculateActual365(previousDate, asOfDate);
            case THIRTY_360 -> calculateThirty360(previousDate, asOfDate);
        };

        // Set the calculated fraction on the accrual
        accrual.setDayCountFraction(dayCountFraction);

        logger.info("Day count fraction calculated for accrual {}: {} (convention: {})",
            accrual.getAccrualId(), dayCountFraction, convention);

        return entityWithMetadata;
    }

    /**
     * Calculates day count fraction using ACT/360 convention.
     * Formula: (actual days between dates) / 360
     */
    private BigDecimal calculateActual360(LocalDate startDate, LocalDate endDate) {
        long actualDays = ChronoUnit.DAYS.between(startDate, endDate);
        BigDecimal days = BigDecimal.valueOf(actualDays);
        BigDecimal divisor = BigDecimal.valueOf(360);
        
        // Use high precision for the fraction
        BigDecimal fraction = days.divide(divisor, 10, RoundingMode.HALF_UP);
        
        logger.debug("ACT/360: {} days / 360 = {}", actualDays, fraction);
        return fraction;
    }

    /**
     * Calculates day count fraction using ACT/365 convention.
     * Formula: (actual days between dates) / 365
     */
    private BigDecimal calculateActual365(LocalDate startDate, LocalDate endDate) {
        long actualDays = ChronoUnit.DAYS.between(startDate, endDate);
        BigDecimal days = BigDecimal.valueOf(actualDays);
        BigDecimal divisor = BigDecimal.valueOf(365);
        
        // Use high precision for the fraction
        BigDecimal fraction = days.divide(divisor, 10, RoundingMode.HALF_UP);
        
        logger.debug("ACT/365: {} days / 365 = {}", actualDays, fraction);
        return fraction;
    }

    /**
     * Calculates day count fraction using 30/360 convention.
     * Formula: ((Y2-Y1)*360 + (M2-M1)*30 + (D2-D1)) / 360
     * 
     * This is a simplified implementation. Production systems may need to handle
     * various 30/360 variants (US, European, etc.)
     */
    private BigDecimal calculateThirty360(LocalDate startDate, LocalDate endDate) {
        int y1 = startDate.getYear();
        int m1 = startDate.getMonthValue();
        int d1 = startDate.getDayOfMonth();
        
        int y2 = endDate.getYear();
        int m2 = endDate.getMonthValue();
        int d2 = endDate.getDayOfMonth();
        
        // Adjust day values according to 30/360 rules
        // If D1 is 31, change to 30
        if (d1 == 31) {
            d1 = 30;
        }
        
        // If D2 is 31 and D1 is 30 or 31, change D2 to 30
        if (d2 == 31 && d1 >= 30) {
            d2 = 30;
        }
        
        // Calculate the number of days using 30/360 formula
        int days = (y2 - y1) * 360 + (m2 - m1) * 30 + (d2 - d1);
        
        BigDecimal daysBD = BigDecimal.valueOf(days);
        BigDecimal divisor = BigDecimal.valueOf(360);
        
        // Use high precision for the fraction
        BigDecimal fraction = daysBD.divide(divisor, 10, RoundingMode.HALF_UP);
        
        logger.debug("30/360: {} days / 360 = {}", days, fraction);
        return fraction;
    }
}

