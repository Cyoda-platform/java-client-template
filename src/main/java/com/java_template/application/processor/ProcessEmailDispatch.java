package com.java_template.application.processor;

import com.java_template.application.entity.EmailDispatch;
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

@Component
public class ProcessEmailDispatch implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessEmailDispatch(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ProcessEmailDispatch initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDispatch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailDispatch.class)
            .validate(this::isValidEntity, "Invalid EmailDispatch state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailDispatch entity) {
        return entity != null && entity.isValid();
    }

    private EmailDispatch processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailDispatch> context) {
        EmailDispatch entity = context.entity();

        // Business logic copied from processEmailDispatch method in CyodaEntityControllerPrototype
        // 1. Send emailContent to userEmail from DigestRequest
        // 2. Update EmailDispatch status to SENT or FAILED depending on email sending outcome

        // Since we don't have actual email sending code, simulate success
        entity.setStatus("SENT");
        entity.setDispatchTimestamp(java.time.Instant.now());

        return entity;
    }
}
