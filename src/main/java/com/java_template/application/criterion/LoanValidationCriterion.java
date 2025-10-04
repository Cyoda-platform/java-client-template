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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ABOUTME: This criterion validates loan entities against business rules
 * for funding date, maturity, and financial constraints.
 */
@Component
public class LoanValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public LoanValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Loan validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Loan.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {
        Loan loan = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (loan == null) {
            logger.warn("Loan entity is null");
            return EvaluationOutcome.fail("Loan entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!loan.isValid()) {
            logger.warn("Loan entity is not valid: {}", loan.getLoanId());
            return EvaluationOutcome.fail("Loan entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Business rule: Funding date should not be in the past
        if (loan.getFundingDate() != null && loan.getFundingDate().isBefore(LocalDate.now())) {
            logger.warn("Loan funding date is in the past: {}", loan.getFundingDate());
            return EvaluationOutcome.fail("Funding date cannot be in the past", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Business rule: Maturity date should be after funding date
        if (loan.getFundingDate() != null && loan.getMaturityDate() != null && 
            loan.getMaturityDate().isBefore(loan.getFundingDate())) {
            logger.warn("Loan maturity date is before funding date");
            return EvaluationOutcome.fail("Maturity date must be after funding date", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Business rule: Principal amount should be positive
        if (loan.getPrincipalAmount() != null && loan.getPrincipalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Loan principal amount is not positive: {}", loan.getPrincipalAmount());
            return EvaluationOutcome.fail("Principal amount must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Business rule: APR should be within reasonable range (1% to 25%)
        if (loan.getApr() != null && 
            (loan.getApr().compareTo(new BigDecimal("0.01")) < 0 || loan.getApr().compareTo(new BigDecimal("0.25")) > 0)) {
            logger.warn("Loan APR is outside acceptable range: {}", loan.getApr());
            return EvaluationOutcome.fail("APR must be between 1% and 25%", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
