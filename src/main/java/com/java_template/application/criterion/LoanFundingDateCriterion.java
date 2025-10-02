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

import java.time.LocalDate;

/**
 * ABOUTME: This criterion checks if a funded loan should transition to active state
 * by comparing the funded date with the current date.
 */
@Component
public class LoanFundingDateCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(LoanFundingDateCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public LoanFundingDateCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Loan.class, this::checkFundingDateLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private com.java_template.common.serializer.EvaluationOutcome checkFundingDateLogic(com.java_template.common.serializer.CriteriaSerializer.CriteriaEntityEvaluationContext<Loan> context) {
        Loan loan = context.entityWithMetadata().entity();
        
        if (loan.getFundedDate() == null) {
            logger.debug("Loan {} has no funded date, criterion not met", loan.getLoanId());
            return com.java_template.common.serializer.EvaluationOutcome.fail("No funded date");
        }

        LocalDate today = LocalDate.now();
        boolean shouldActivate = !loan.getFundedDate().isAfter(today);

        logger.debug("Loan {} funding date criterion: funded={}, today={}, result={}",
                    loan.getLoanId(), loan.getFundedDate(), today, shouldActivate);

        return shouldActivate ?
            com.java_template.common.serializer.EvaluationOutcome.success() :
            com.java_template.common.serializer.EvaluationOutcome.fail("Funding date not reached");
    }
}
