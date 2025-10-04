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

@Component
public class ApproveLoan implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ApproveLoan.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ApproveLoan(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntityWithMetadata(Loan.class)
                .validate(this::isValid, "Invalid loan").map(this::process).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValid(EntityWithMetadata<Loan> entityWithMetadata) {
        return entityWithMetadata.entity() != null && entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Loan> process(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {
        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        logger.info("Loan {} approved", entityWithMetadata.entity().getLoanId());
        return entityWithMetadata;
    }
}
