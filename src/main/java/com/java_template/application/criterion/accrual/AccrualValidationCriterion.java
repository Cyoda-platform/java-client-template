package com.java_template.application.criterion.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.common.serializer.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ABOUTME: Composite criterion that validates all accrual creation requirements.
 * Combines IsBusinessDay, LoanActiveOnDate, NotDuplicateAccrual, and principal amount checks.
 */
@Component
public class AccrualValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final IsBusinessDayCriterion isBusinessDayCriterion;
    private final LoanActiveOnDateCriterion loanActiveOnDateCriterion;
    private final NotDuplicateAccrualCriterion notDuplicateAccrualCriterion;
    private final String className = this.getClass().getSimpleName();

    public AccrualValidationCriterion(
            SerializerFactory serializerFactory,
            EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.isBusinessDayCriterion = new IsBusinessDayCriterion(serializerFactory);
        this.loanActiveOnDateCriterion = new LoanActiveOnDateCriterion(serializerFactory, entityService);
        this.notDuplicateAccrualCriterion = new NotDuplicateAccrualCriterion(serializerFactory, entityService);
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AccrualValidation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates all accrual creation requirements.
     * This method is public to allow the negative criterion and error processor to call it.
     */
    public EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        Accrual accrual = context.entityWithMetadata().entity();

        // Check 1: Is business day
        EvaluationOutcome businessDayOutcome = isBusinessDayCriterion.validateBusinessDay(context);

        if (businessDayOutcome != null && businessDayOutcome.isFailure()) {
            logger.debug("Business day validation failed for accrual: {}", accrual.getAccrualId());
            return businessDayOutcome;
        }

        // Check 2: Loan active on date
        EvaluationOutcome loanActiveOutcome = loanActiveOnDateCriterion.validateLoanActive(context);

        if (loanActiveOutcome != null && loanActiveOutcome.isFailure()) {
            logger.debug("Loan active validation failed for accrual: {}", accrual.getAccrualId());
            return loanActiveOutcome;
        }

        // Check 3: Not duplicate accrual
        EvaluationOutcome notDuplicateOutcome = notDuplicateAccrualCriterion.validateNotDuplicate(context);

        if (notDuplicateOutcome != null && notDuplicateOutcome.isFailure()) {
            logger.debug("Duplicate accrual validation failed for accrual: {}", accrual.getAccrualId());
            return notDuplicateOutcome;
        }

        // Check 4: Principal amount > 0
        if (accrual.getPrincipalSnapshot() == null) {
            logger.warn("Principal snapshot is null for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail(
                "Principal snapshot is required",
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        BigDecimal principalAmount = accrual.getPrincipalSnapshot().getAmount();
        if (principalAmount == null) {
            logger.warn("Principal amount is null for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail(
                "Principal amount is required",
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        if (principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Principal amount {} is not greater than zero for accrual: {}",
                principalAmount, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("Principal amount %s must be greater than zero", principalAmount),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("All accrual validations passed for accrual: {}", accrual.getAccrualId());
        return EvaluationOutcome.success();
    }
}

