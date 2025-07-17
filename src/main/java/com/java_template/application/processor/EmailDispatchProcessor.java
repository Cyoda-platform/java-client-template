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

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(EmailDispatch.class)
            .validate(this::isValidEntity, "Invalid EmailDispatch entity state")
            .map(this::processEmailDispatch)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchProcessor".equals(modelSpec.operationName()) &&
               "emailDispatch".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Validation method
    private boolean isValidEntity(EmailDispatch emailDispatch) {
        return emailDispatch.isValid();
    }

    // Business processing logic
    private EmailDispatch processEmailDispatch(EmailDispatch emailDispatch) {
        // Example logic: Update status from PENDING to SENT and set emailSentAt timestamp
        if (emailDispatch.getStatus() == EmailDispatch.Status.PENDING) {
            emailDispatch.setStatus(EmailDispatch.Status.SENT);
            emailDispatch.setEmailSentAt(new java.sql.Timestamp(System.currentTimeMillis()));
            logger.info("EmailDispatch status updated to SENT and emailSentAt timestamp set");
        }
        return emailDispatch;
    }
}
