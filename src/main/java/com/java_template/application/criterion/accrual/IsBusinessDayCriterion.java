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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Criterion to validate that the AsOfDate is a valid business day.
 *
 * Business days are defined as:
 * - Monday through Friday (not Saturday or Sunday)
 * - Not a configured holiday
 *
 * This is a pure function with no side effects.
 */
@Component
public class IsBusinessDayCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // TODO: In production, this should be loaded from a business calendar service
    // For now, using a simple hardcoded set of US federal holidays for 2025
    private static final Set<LocalDate> HOLIDAYS = new HashSet<>();

    static {
        // 2025 US Federal Holidays (example)
        HOLIDAYS.add(LocalDate.of(2025, 1, 1));   // New Year's Day
        HOLIDAYS.add(LocalDate.of(2025, 1, 20));  // MLK Day
        HOLIDAYS.add(LocalDate.of(2025, 2, 17));  // Presidents Day
        HOLIDAYS.add(LocalDate.of(2025, 5, 26));  // Memorial Day
        HOLIDAYS.add(LocalDate.of(2025, 7, 4));   // Independence Day
        HOLIDAYS.add(LocalDate.of(2025, 9, 1));   // Labor Day
        HOLIDAYS.add(LocalDate.of(2025, 10, 13)); // Columbus Day
        HOLIDAYS.add(LocalDate.of(2025, 11, 11)); // Veterans Day
        HOLIDAYS.add(LocalDate.of(2025, 11, 27)); // Thanksgiving
        HOLIDAYS.add(LocalDate.of(2025, 12, 25)); // Christmas
    }

    public IsBusinessDayCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsBusinessDay criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateBusinessDay)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsBusinessDay".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the accrual's asOfDate is a business day.
     *
     * @param context The criterion evaluation context containing the accrual
     * @return EvaluationOutcome.success() if valid business day, otherwise failure with reason
     */
    private EvaluationOutcome validateBusinessDay(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        Accrual accrual = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (accrual == null) {
            logger.warn("Accrual entity is null");
            return EvaluationOutcome.fail("Accrual entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        LocalDate asOfDate = accrual.getAsOfDate();

        // Check if asOfDate is null
        if (asOfDate == null) {
            logger.warn("AsOfDate is null for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("AsOfDate is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if it's a weekend
        DayOfWeek dayOfWeek = asOfDate.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            logger.warn("AsOfDate {} is a weekend for accrual: {}", asOfDate, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("AsOfDate %s is a weekend (%s)", asOfDate, dayOfWeek),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check if it's a holiday
        if (HOLIDAYS.contains(asOfDate)) {
            logger.warn("AsOfDate {} is a holiday for accrual: {}", asOfDate, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("AsOfDate %s is a holiday", asOfDate),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("AsOfDate {} is a valid business day for accrual: {}", asOfDate, accrual.getAccrualId());
        return EvaluationOutcome.success();
    }
}

