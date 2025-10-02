package com.java_template.application.processor;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ABOUTME: This processor handles loan funding by setting initial balances,
 * recording funding details, and preparing the loan for activation.
 */
@Component
public class FundLoanProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FundLoanProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FundLoanProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::fundLoanLogic)
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

    private EntityWithMetadata<Loan> fundLoanLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Funding loan: {}", loan.getLoanId());

        // Set funding details
        LocalDate fundingDate = LocalDate.now();
        loan.setFundedDate(fundingDate);
        
        // If funded amount not set, use principal amount
        if (loan.getFundedAmount() == null) {
            loan.setFundedAmount(loan.getPrincipalAmount());
        }

        // Set initial balances
        loan.setOutstandingPrincipal(loan.getFundedAmount());
        loan.setAccruedInterest(BigDecimal.ZERO);
        loan.setTotalInterestReceivable(BigDecimal.ZERO);

        // Calculate maturity date
        LocalDate maturityDate = calculateMaturityDate(fundingDate, loan.getTermMonths());
        loan.setMaturityDate(maturityDate);

        // Set next due date (first payment due)
        LocalDate nextDueDate = calculateNextDueDate(fundingDate, loan.getRepaymentDay());
        loan.setNextDueDate(nextDueDate);

        // Update metadata
        loan.setUpdatedAt(LocalDateTime.now());

        logger.info("Loan {} funded successfully. Amount: {}, Maturity: {}", 
                   loan.getLoanId(), loan.getFundedAmount(), maturityDate);

        return entityWithMetadata;
    }

    private LocalDate calculateMaturityDate(LocalDate fundingDate, Integer termMonths) {
        return fundingDate.plusMonths(termMonths);
    }

    private LocalDate calculateNextDueDate(LocalDate fundingDate, Integer repaymentDay) {
        LocalDate nextMonth = fundingDate.plusMonths(1);
        
        // Handle month-end scenarios
        int lastDayOfMonth = nextMonth.lengthOfMonth();
        int targetDay = Math.min(repaymentDay, lastDayOfMonth);
        
        return nextMonth.withDayOfMonth(targetDay);
    }
}
