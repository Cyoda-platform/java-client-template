package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class SendWelcomeEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendWelcomeEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendWelcomeEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendWelcomeEmail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid Subscriber entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.getEmail() != null && !entity.getEmail().isEmpty();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        // Simulate sending welcome email
        logger.info("Sending welcome email to: {}", entity.getEmail());
        // Actual email sending logic would go here
        return entity;
    }
}
