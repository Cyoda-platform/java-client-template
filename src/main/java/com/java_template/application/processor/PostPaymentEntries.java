package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
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
public class PostPaymentEntries implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(PostPaymentEntries.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PostPaymentEntries(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntityWithMetadata(Payment.class)
                .validate(this::isValid, "Invalid Payment").map(this::process).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValid(EntityWithMetadata<Payment> entityWithMetadata) {
        return entityWithMetadata.entity() != null && entityWithMetadata.entity().isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Payment> process(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        logger.info("PostPaymentEntries completed for entity: {}", entityWithMetadata.metadata().getId());
        return entityWithMetadata;
    }
}
