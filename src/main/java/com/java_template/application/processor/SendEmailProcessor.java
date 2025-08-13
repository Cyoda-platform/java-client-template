package com.java_template.application.processor;

import com.java_template.application.entity.emaildispatch.version_1.EmailDispatch;
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
public class SendEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendEmail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailDispatch.class)
            .validate(this::isValidEntity, "Invalid EmailDispatch entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailDispatch entity) {
        return entity != null && entity.getSubscriberEmail() != null && !entity.getSubscriberEmail().isEmpty();
    }

    private EmailDispatch processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailDispatch> context) {
        EmailDispatch entity = context.entity();
        // Simulate sending email
        logger.info("Sending cat fact email to: {}", entity.getSubscriberEmail());
        // Here would be the integration with an email service provider
        entity.setDispatchedAt(java.time.Instant.now().toString());
        logger.info("Email sent at: {}", entity.getDispatchedAt());
        return entity;
    }
}
