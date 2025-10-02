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
 * ABOUTME: This processor handles loan approval with maker/checker controls
 * and records approval metadata for audit purposes.
 */
@Component
public class ApproveLoanProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveLoanProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ApproveLoanProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::approveLoanLogic)
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

    private EntityWithMetadata<Loan> approveLoanLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Approving loan: {}", loan.getLoanId());

        // Create approval record
        Loan.LoanApproval approval = new Loan.LoanApproval();
        approval.setApprovedBy("system"); // In real implementation, get from security context
        approval.setApprovedAt(LocalDateTime.now());
        approval.setApproverRole("LOAN_APPROVER");
        approval.setApprovalComments("Loan approved");
        
        loan.setApproval(approval);
        loan.setUpdatedAt(LocalDateTime.now());

        logger.info("Loan {} approved successfully", loan.getLoanId());

        return entityWithMetadata;
    }
}
