package com.java_template.application.criterion.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Criterion to validate that the loan was ACTIVE on the AsOfDate.
 *
 * A loan is considered active for accrual purposes if:
 * - The loan exists
 * - The asOfDate is on or after the funding date
 * - The asOfDate is before the maturity date
 * - The loan is not in a NON_ACCRUAL state (if such field exists)
 * - The loan has not been charged off
 *
 * This is a pure function with no side effects.
 */
@Component
public class LoanActiveOnDateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoanActiveOnDateCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking LoanActiveOnDate criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateLoanActive)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "LoanActiveOnDate".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the loan referenced by the accrual was active on the asOfDate.
     *
     * @param context The criterion evaluation context containing the accrual
     * @return EvaluationOutcome.success() if loan was active, otherwise failure with reason
     */
    public EvaluationOutcome validateLoanActive(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        Accrual accrual = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (accrual == null) {
            logger.warn("Accrual entity is null");
            return EvaluationOutcome.fail("Accrual entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        String loanId = accrual.getLoanId();
        LocalDate asOfDate = accrual.getAsOfDate();

        // Check required fields
        if (loanId == null || loanId.trim().isEmpty()) {
            logger.warn("LoanId is null or empty for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("LoanId is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (asOfDate == null) {
            logger.warn("AsOfDate is null for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("AsOfDate is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Retrieve the loan
        ModelSpec loanModelSpec = new ModelSpec()
            .withName(Loan.ENTITY_NAME)
            .withVersion(Loan.ENTITY_VERSION);

        EntityWithMetadata<Loan> loanWithMetadata;
        try {
            loanWithMetadata = entityService.findByBusinessId(
                loanModelSpec,
                loanId,
                "loanId",
                Loan.class
            );
        } catch (Exception e) {
            logger.error("Error retrieving loan {} for accrual {}: {}", loanId, accrual.getAccrualId(), e.getMessage());
            return EvaluationOutcome.fail(
                String.format("Error retrieving loan %s: %s", loanId, e.getMessage()),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        if (loanWithMetadata == null) {
            logger.warn("Loan {} not found for accrual: {}", loanId, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("Loan %s not found", loanId),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        Loan loan = loanWithMetadata.entity();

        // Check if loan was funded by asOfDate
        if (loan.getFundingDate() == null) {
            logger.warn("Loan {} has no funding date for accrual: {}", loanId, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("Loan %s has no funding date", loanId),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        if (asOfDate.isBefore(loan.getFundingDate())) {
            logger.warn("AsOfDate {} is before funding date {} for loan {} in accrual: {}",
                asOfDate, loan.getFundingDate(), loanId, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("AsOfDate %s is before loan funding date %s", asOfDate, loan.getFundingDate()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check if loan has matured by asOfDate
        if (loan.getMaturityDate() == null) {
            logger.warn("Loan {} has no maturity date for accrual: {}", loanId, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("Loan %s has no maturity date", loanId),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        if (!asOfDate.isBefore(loan.getMaturityDate())) {
            logger.warn("AsOfDate {} is on or after maturity date {} for loan {} in accrual: {}",
                asOfDate, loan.getMaturityDate(), loanId, accrual.getAccrualId());
            return EvaluationOutcome.fail(
                String.format("AsOfDate %s is on or after loan maturity date %s", asOfDate, loan.getMaturityDate()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // TODO: Add NON_ACCRUAL status check when loan entity has such a field
        // TODO: Add charge-off status check when loan entity has such a field

        logger.debug("Loan {} was active on {} for accrual: {}", loanId, asOfDate, accrual.getAccrualId());
        return EvaluationOutcome.success();
    }
}

