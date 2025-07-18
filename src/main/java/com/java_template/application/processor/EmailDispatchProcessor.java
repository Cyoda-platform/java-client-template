package com.java_template.application.processor;

import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailDispatchProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public EmailDispatchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("EmailDispatchProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDispatch for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(EmailDispatch.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchProcessor".equals(modelSpec.operationName()) &&
                "emailDispatch".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(EmailDispatch entity) {
        return entity.isValid();
    }

    private EmailDispatch processEntityLogic(EmailDispatch entity) {
        // Business logic for processing EmailDispatch entity
        // According to functional requirements:
        // 1. Initial State: Create EmailDispatch entities with QUEUED status for each recipient
        // 2. Email Sending: Dispatch emails with pet data digest content
        // 3. Update EmailDispatch status to SENT or FAILED

        // Note: Actual email sending mechanism is not part of this processor
        // Here we simulate the status update logic based on current status

        if ("QUEUED".equalsIgnoreCase(entity.getStatus())) {
            // Simulate sending email
            boolean emailSentSuccessfully = sendEmail(entity);
            if (emailSentSuccessfully) {
                entity.setStatus("SENT");
            } else {
                entity.setStatus("FAILED");
            }
        }
        // For other statuses, no changes
        return entity;
    }

    private boolean sendEmail(EmailDispatch entity) {
        // Placeholder for actual email sending logic
        // For now, we simulate success
        logger.info("Sending email to: {} with subject: {}", entity.getRecipient(), entity.getSubject());
        return true;
    }
}
