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

        return serializer.withRequest(request)
                .toEntity(EmailDispatch.class)
                .validate(EmailDispatch::isValid, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchProcessor".equals(modelSpec.operationName()) &&
                "emailDispatch".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EmailDispatch processEntityLogic(EmailDispatch entity) {
        // Business logic based on functional requirements:
        // 1. Initial State: EmailDispatch created with QUEUED status.
        // 2. Sending: Send compiled digest to the email address, using specified format.
        // 3. Completion: Update EmailDispatch status to SENT or FAILED.

        logger.info("Sending email for EmailDispatch ID: {}", entity.getId());

        // Simulate sending email logic
        boolean emailSentSuccessfully = sendEmail(entity);

        if (emailSentSuccessfully) {
            entity.setStatus(EmailDispatch.StatusEnum.SENT);
            logger.info("Email sent successfully for EmailDispatch ID: {}", entity.getId());
        } else {
            entity.setStatus(EmailDispatch.StatusEnum.FAILED);
            logger.error("Email sending failed for EmailDispatch ID: {}", entity.getId());
        }

        return entity;
    }

    private boolean sendEmail(EmailDispatch entity) {
        // Implementation of actual email sending logic should be here
        // For now, simulate success
        return true;
    }
}
