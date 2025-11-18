package com.java_template.application.criterion;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriterionEvaluationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionEvaluationResponse;
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
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionEvaluationResponse check(CyodaEventContext<EntityCriterionEvaluationRequest> context) {
        EntityCriterionEvaluationRequest request = context.getEvent();
        logger.info("Evaluating LoanApprovalCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::evaluateLoanApprovalCriteria)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Loan> entityWithMetadata) {
        Loan loan = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return loan != null && loan.isValid() && technicalId != null;
    }

    private boolean evaluateLoanApprovalCriteria(
            CriterionSerializer.CriterionEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Evaluating approval criteria for loan: {}", loan.getLoanId());

        // Check loan amount
        if (loan.getLoanAmount().compareTo(MAX_LOAN_AMOUNT) > 0) {
            logger.warn("Loan {} exceeds maximum amount", loan.getLoanId());
            return false;
        }

        // Check loan term
        if (loan.getLoanTermMonths() < MIN_LOAN_TERM || loan.getLoanTermMonths() > MAX_LOAN_TERM) {
            logger.warn("Loan {} has invalid term: {}", loan.getLoanId(), loan.getLoanTermMonths());
            return false;
        }

        // Check interest rate is reasonable
        if (loan.getInterestRate().compareTo(BigDecimal.ZERO) < 0 || 
            loan.getInterestRate().compareTo(new BigDecimal("50")) > 0) {
            logger.warn("Loan {} has invalid interest rate: {}", loan.getLoanId(), loan.getInterestRate());
            return false;
        }

        // Check borrower information is complete
        if (loan.getBorrowerEmail() == null || loan.getBorrowerEmail().trim().isEmpty()) {
            logger.warn("Loan {} missing borrower email", loan.getLoanId());
            return false;
        }

        logger.info("Loan {} meets approval criteria", loan.getLoanId());
        return true;
    }
}

