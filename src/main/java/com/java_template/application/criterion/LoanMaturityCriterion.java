package com.java_template.application.criterion;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationResponse;
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
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntity(Loan.class)
                .validate(this::isValidLoan, "Invalid loan entity")
                .map(this::checkMaturityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidLoan(Loan loan) {
        return loan != null && loan.isValid();
    }

    private boolean checkMaturityLogic(CriterionSerializer.CriterionEntityResponseExecutionContext<Loan> context) {
        Loan loan = context.entity();
        
        if (loan.getMaturityDate() == null) {
            logger.debug("Loan {} has no maturity date, criterion not met", loan.getLoanId());
            return false;
        }

        LocalDate today = LocalDate.now();
        boolean maturityReached = !loan.getMaturityDate().isAfter(today);
        
        BigDecimal outstandingPrincipal = loan.getOutstandingPrincipal();
        boolean principalPaidOff = outstandingPrincipal == null || outstandingPrincipal.compareTo(BigDecimal.ZERO) == 0;

        boolean shouldClose = maturityReached && principalPaidOff;

        logger.debug("Loan {} maturity criterion: maturity={}, today={}, principal={}, result={}", 
                    loan.getLoanId(), loan.getMaturityDate(), today, outstandingPrincipal, shouldClose);

        return shouldClose;
    }
}
