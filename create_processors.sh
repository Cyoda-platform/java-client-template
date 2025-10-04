#\!/bin/bash

# List of processors to create with their entity types
declare -A processors=(
    ["ValidatePayment"]="Payment"
    ["MatchPaymentToLoan"]="Payment"
    ["ManualMatchPayment"]="Payment"
    ["ReturnPayment"]="Payment"
    ["PostPaymentEntries"]="Payment"
    ["ValidateAccrualRequest"]="Accrual"
    ["PostAccrualEntries"]="Accrual"
    ["ValidateQuoteRequest"]="SettlementQuote"
    ["CalculateSettlementAmount"]="SettlementQuote"
    ["AcceptSettlementQuote"]="SettlementQuote"
    ["SummarizePeriod"]="GLBatch"
    ["ValidateBatchBalance"]="GLBatch"
    ["RecordCheckerApproval"]="GLBatch"
    ["ExportGLBatch"]="GLBatch"
    ["ConfirmGLPosting"]="GLBatch"
    ["ValidateGLLine"]="GLLine"
    ["ProcessLoanPayment"]="Loan"
    ["GenerateSettlementQuote"]="Loan"
    ["ProcessEarlySettlement"]="Loan"
    ["ProcessFinalPayment"]="Loan"
    ["CloseLoan"]="Loan"
)

for processor in "${\!processors[@]}"; do
    entity="${processors[$processor]}"
    
    cat > "src/main/java/com/java_template/application/processor/${processor}.java" << PROCESSOR_EOF
package com.java_template.application.processor;

import com.java_template.application.entity.$(echo $entity | tr '[:upper:]' '[:lower:]')/version_1/${entity};
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

@Component
public class ${processor} implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(${processor}.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ${processor}(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntityWithMetadata(${entity}.class)
                .validate(this::isValid, "Invalid ${entity}").map(this::process).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValid(EntityWithMetadata<${entity}> entityWithMetadata) {
        return entityWithMetadata.entity() \!= null && entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<${entity}> process(ProcessorSerializer.ProcessorEntityResponseExecutionContext<${entity}> context) {
        EntityWithMetadata<${entity}> entityWithMetadata = context.entityResponse();
        logger.info("${processor} completed for entity: {}", entityWithMetadata.metadata().getId());
        return entityWithMetadata;
    }
}
PROCESSOR_EOF

    echo "Created processor: ${processor}"
done

echo "All processors created successfully\!"
