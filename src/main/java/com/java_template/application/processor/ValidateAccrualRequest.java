package com.java_template.application.processor;

import com.java_template.application.entity.accrual.version_1.Accrual;
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
public class ValidateAccrualRequest implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ValidateAccrualRequest.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateAccrualRequest(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntityWithMetadata(Accrual.class)
                .validate(this::isValid, "Invalid Accrual").map(this::process).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValid(EntityWithMetadata<Accrual> entityWithMetadata) {
        return entityWithMetadata.entity() != null && entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Accrual> process(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {
        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        logger.info("ValidateAccrualRequest completed for entity: {}", entityWithMetadata.metadata().getId());
        return entityWithMetadata;
    }
}
