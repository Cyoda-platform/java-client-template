package com.java_template.application.criterion;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ABOUTME: This criterion checks if a loan should be closed due to maturity
 * by verifying the maturity date has been reached and principal balance is zero.
 */
@Component
public class LoanMaturityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(LoanMaturityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public LoanMaturityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Loan.class, this::checkMaturityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private com.java_template.common.serializer.EvaluationOutcome checkMaturityLogic(CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {
        Loan loan = context.entityWithMetadata().entity();
        
        if (loan.getMaturityDate() == null) {
            logger.debug("Loan {} has no maturity date, criterion not met", loan.getLoanId());
            return com.java_template.common.serializer.EvaluationOutcome.fail("No maturity date");
        }

        LocalDate today = LocalDate.now();
        boolean maturityReached = !loan.getMaturityDate().isAfter(today);

        BigDecimal outstandingPrincipal = loan.getOutstandingPrincipal();
        boolean principalPaidOff = outstandingPrincipal == null || outstandingPrincipal.compareTo(BigDecimal.ZERO) == 0;

        boolean shouldClose = maturityReached && principalPaidOff;

        logger.debug("Loan {} maturity criterion: maturity={}, today={}, principal={}, result={}",
                    loan.getLoanId(), loan.getMaturityDate(), today, outstandingPrincipal, shouldClose);

        return shouldClose ?
            com.java_template.common.serializer.EvaluationOutcome.success() :
            com.java_template.common.serializer.EvaluationOutcome.fail("Maturity conditions not met");
    }
}
