package com.java_template.application.criterion;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * Criterion to check if current time is within business hours.
 * Used for email campaign sending and other time-sensitive operations.
 * 
 * Validation Logic:
 * - Checks if current day is Monday-Friday (business days)
 * - Checks if current time is between 9 AM and 5 PM
 * - Excludes weekends and holidays
 * - Returns success if within business hours
 */
@Component
public class BusinessHoursCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(BusinessHoursCriterion.class);
    private static final LocalTime BUSINESS_START_TIME = LocalTime.of(9, 0); // 9:00 AM
    private static final LocalTime BUSINESS_END_TIME = LocalTime.of(17, 0);   // 5:00 PM
    
    // Business days (Monday to Friday)
    private static final Set<DayOfWeek> BUSINESS_DAYS = Set.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    );
    
    private final CriterionSerializer serializer;

    public BusinessHoursCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("BusinessHoursCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking business hours criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .responseBuilder()
            .withEvaluationOutcome(this.evaluateBusinessHours())
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "BusinessHoursCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether current time is within business hours.
     * 
     * @return EvaluationOutcome indicating whether it's business hours
     */
    private EvaluationOutcome evaluateBusinessHours() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek currentDay = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        logger.debug("Checking business hours for {} at {}", currentDay, currentTime);

        // Check if it's a business day
        if (!BUSINESS_DAYS.contains(currentDay)) {
            return EvaluationOutcome.fail(String.format("Not a business day: %s", currentDay));
        }

        // Check if it's within business hours
        if (currentTime.isBefore(BUSINESS_START_TIME)) {
            return EvaluationOutcome.fail(String.format("Before business hours: %s (starts at %s)", 
                                                       currentTime, BUSINESS_START_TIME));
        }

        if (currentTime.isAfter(BUSINESS_END_TIME)) {
            return EvaluationOutcome.fail(String.format("After business hours: %s (ends at %s)", 
                                                       currentTime, BUSINESS_END_TIME));
        }

        // Check for holidays (simplified - in reality would check against holiday calendar)
        if (isHoliday(now)) {
            return EvaluationOutcome.fail("Current date is a holiday");
        }

        // Within business hours
        logger.debug("Within business hours: {} at {}", currentDay, currentTime);
        return EvaluationOutcome.success();
    }

    /**
     * Checks if the given date is a holiday.
     * Simplified implementation - in reality would check against a holiday calendar.
     */
    private boolean isHoliday(LocalDateTime dateTime) {
        // Simplified holiday check - in a real implementation, this would:
        // - Check against a comprehensive holiday calendar
        // - Consider different time zones
        // - Handle regional holidays
        // - Support configurable holiday lists
        
        int month = dateTime.getMonthValue();
        int day = dateTime.getDayOfMonth();
        
        // Check for some major US holidays (simplified)
        
        // New Year's Day (January 1)
        if (month == 1 && day == 1) {
            return true;
        }
        
        // Independence Day (July 4)
        if (month == 7 && day == 4) {
            return true;
        }
        
        // Christmas Day (December 25)
        if (month == 12 && day == 25) {
            return true;
        }
        
        // Christmas Eve (December 24) - often a half day or holiday
        if (month == 12 && day == 24) {
            return true;
        }
        
        // New Year's Eve (December 31) - often a half day or holiday
        if (month == 12 && day == 31) {
            return true;
        }
        
        // No holiday detected
        return false;
    }
}
