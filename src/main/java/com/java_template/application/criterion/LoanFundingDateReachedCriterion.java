package com.java_template.application.criterion;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.serializer.*;
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
 * ABOUTME: This criterion checks if the loan's funding date has been reached (is today or in the past).
 * Used to automatically activate loans when their funding date arrives.
 */
@Component
public class LoanFundingDateReachedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public LoanFundingDateReachedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if loan funding date has been reached for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Loan.class, this::checkFundingDateReached)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome checkFundingDateReached(CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {
        Loan loan = context.entityWithMetadata().entity();

        if (loan.getFundingDate() == null) {
            logger.debug("Loan has no funding date set");
            return EvaluationOutcome.fail("Funding date is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        LocalDate today = LocalDate.now();
        boolean fundingDateReached = !loan.getFundingDate().isAfter(today);

        if (fundingDateReached) {
            logger.debug("Loan funding date {} has been reached (today: {})", loan.getFundingDate(), today);
            return EvaluationOutcome.success();
        } else {
            logger.debug("Loan funding date {} has not been reached yet (today: {})", loan.getFundingDate(), today);
            return EvaluationOutcome.fail(
                String.format("Funding date %s has not been reached yet", loan.getFundingDate()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }
    }
}

