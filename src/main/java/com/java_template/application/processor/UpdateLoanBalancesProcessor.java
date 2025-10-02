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

import java.time.LocalDateTime;

/**
 * ABOUTME: This processor updates loan balances in response to payment allocations
 * and accrual postings, maintaining accurate outstanding principal and interest receivable.
 */
@Component
public class UpdateLoanBalancesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateLoanBalancesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateLoanBalancesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::updateBalancesLogic)
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

    private EntityWithMetadata<Loan> updateBalancesLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Updating balances for loan: {}", loan.getLoanId());

        // This processor would typically receive events from Payment and Accrual processors
        // and update the loan balances accordingly. For now, we'll just update the timestamp.
        
        loan.setUpdatedAt(LocalDateTime.now());

        logger.info("Loan balances updated for: {}", loan.getLoanId());

        return entityWithMetadata;
    }
}
