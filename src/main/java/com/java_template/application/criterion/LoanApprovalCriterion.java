package com.java_template.application.criterion;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * ABOUTME: Criterion for evaluating loan approval eligibility.
 * Pure function that checks if a loan meets approval criteria without side effects.
 */
@Component
public class LoanApprovalCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(LoanApprovalCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    // Approval criteria thresholds
    private static final BigDecimal MAX_LOAN_AMOUNT = new BigDecimal("1000000");
    private static final Integer MIN_LOAN_TERM = 6;
    private static final Integer MAX_LOAN_TERM = 360;

    public LoanApprovalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Loan approval criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Loan.class, this::validateLoanApprovalCriteria)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateLoanApprovalCriteria(
            CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {

        Loan loan = context.entityWithMetadata().entity();

        if (loan == null) {
            logger.warn("Loan is null");
            return EvaluationOutcome.fail("Loan is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!loan.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Loan is not valid");
            return EvaluationOutcome.fail("Loan is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.debug("Evaluating approval criteria for loan: {}", loan.getLoanId());

        // Check loan amount
        if (loan.getLoanAmount().compareTo(MAX_LOAN_AMOUNT) > 0) {
            logger.warn("Loan {} exceeds maximum amount", loan.getLoanId());
            return EvaluationOutcome.fail(
                String.format("Loan amount exceeds maximum of %s", MAX_LOAN_AMOUNT),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check loan term
        if (loan.getLoanTermMonths() < MIN_LOAN_TERM || loan.getLoanTermMonths() > MAX_LOAN_TERM) {
            logger.warn("Loan {} has invalid term: {}", loan.getLoanId(), loan.getLoanTermMonths());
            return EvaluationOutcome.fail(
                String.format("Loan term must be between %d and %d months", MIN_LOAN_TERM, MAX_LOAN_TERM),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check interest rate is reasonable
        if (loan.getInterestRate().compareTo(BigDecimal.ZERO) < 0 ||
            loan.getInterestRate().compareTo(new BigDecimal("50")) > 0) {
            logger.warn("Loan {} has invalid interest rate: {}", loan.getLoanId(), loan.getInterestRate());
            return EvaluationOutcome.fail(
                "Interest rate must be between 0% and 50%",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check borrower information is complete
        if (loan.getBorrowerEmail() == null || loan.getBorrowerEmail().trim().isEmpty()) {
            logger.warn("Loan {} missing borrower email", loan.getLoanId());
            return EvaluationOutcome.fail(
                "Borrower email is required",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.info("Loan {} meets approval criteria", loan.getLoanId());
        return EvaluationOutcome.success();
    }
}

