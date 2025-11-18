package com.java_template.application.processor;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor for loan disbursement workflow transition.
 * Handles business logic when a loan is disbursed to the borrower.
 */
@Component
public class LoanDisbursementProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LoanDisbursementProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public LoanDisbursementProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing LoanDisbursementProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::processLoanDisbursement)
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

    private EntityWithMetadata<Loan> processLoanDisbursement(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Disbursing loan: {} for borrower: {}", loan.getLoanId(), loan.getBorrowerName());

        // Set disbursement date
        loan.setDisbursementDate(LocalDateTime.now());
        
        // Calculate maturity date based on term
        if (loan.getLoanTermMonths() != null) {
            LocalDateTime maturityDate = LocalDateTime.now().plusMonths(loan.getLoanTermMonths());
            loan.setMaturityDate(maturityDate);
        }
        
        // Update timestamp
        loan.setUpdatedAt(LocalDateTime.now());

        logger.info("Loan {} disbursed successfully", loan.getLoanId());

        return entityWithMetadata;
    }
}

