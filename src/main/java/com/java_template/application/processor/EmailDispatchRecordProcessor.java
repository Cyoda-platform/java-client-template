package com.java_template.application.processor;

import com.java_template.application.entity.EmailDispatchRecord;
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

import java.time.Instant;
import java.util.function.Function;

@Component
public class EmailDispatchRecordProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public EmailDispatchRecordProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("EmailDispatchRecordProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDispatchRecord for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(EmailDispatchRecord.class)
                .validate(EmailDispatchRecord::isValid, "Invalid EmailDispatchRecord entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchRecordProcessor".equals(modelSpec.operationName()) &&
               "emailDispatchRecord".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EmailDispatchRecord processEntityLogic(EmailDispatchRecord entity) {
        // Business logic for email dispatch
        // Step 1: Attempt to send email (simulate email sending here)
        // Step 2: Update dispatchStatus and sentAt timestamp accordingly

        // Simulated email sending logic (replace with actual email service call if available)
        boolean emailSent = sendEmail(entity.getEmail());

        if (emailSent) {
            entity.setDispatchStatus("SENT");
            entity.setSentAt(Instant.now().toString());
        } else {
            entity.setDispatchStatus("FAILED");
            entity.setSentAt(Instant.now().toString());
        }

        return entity;
    }

    // Simulated email sending method
    private boolean sendEmail(String email) {
        logger.info("Sending email to: {}", email);
        // Here you can integrate with an actual email service provider
        // For now, assume email sending always succeeds
        return true;
    }
}
