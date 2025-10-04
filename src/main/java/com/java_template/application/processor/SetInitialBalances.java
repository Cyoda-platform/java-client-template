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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * ABOUTME: This processor sets the initial financial balances when a loan is funded,
 * initializing outstanding principal and accrued interest amounts.
 */
@Component
public class SetInitialBalances implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SetInitialBalances.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SetInitialBalances(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Loan> entityWithMetadata) {
        Loan entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Loan> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Setting initial balances for loan: {}", loan.getLoanId());

        // Set outstanding principal equal to the initial principal amount
        if (loan.getPrincipalAmount() != null) {
            loan.setOutstandingPrincipal(loan.getPrincipalAmount());
            logger.debug("Set outstanding principal to: {}", loan.getOutstandingPrincipal());
        } else {
            throw new IllegalStateException("Principal amount is required to set initial balances");
        }

        // Set accrued interest to zero
        loan.setAccruedInterest(BigDecimal.ZERO);
        logger.debug("Set accrued interest to: {}", loan.getAccruedInterest());

        logger.info("Initial balances set for loan {}: Principal={}, AccruedInterest={}", 
                   loan.getLoanId(), loan.getOutstandingPrincipal(), loan.getAccruedInterest());

        return entityWithMetadata;
    }
}
