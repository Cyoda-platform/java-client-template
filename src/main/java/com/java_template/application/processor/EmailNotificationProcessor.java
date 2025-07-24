package com.java_template.application.processor;

import com.java_template.application.entity.EmailNotification;
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
public class EmailNotificationProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public EmailNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("EmailNotificationProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailNotification for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(EmailNotification.class)
                .validate(EmailNotification::isValid, "Invalid EmailNotification entity")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailNotificationProcessor".equals(modelSpec.operationName()) &&
               "emailNotification".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EmailNotification processEntityLogic(EmailNotification entity) {
        try {
            logger.info("Processing EmailNotification with subscriberEmail: {}", entity.getSubscriberEmail());

            boolean emailSent = sendEmail(entity.getSubscriberEmail(), entity.getNotificationDate());
            if (emailSent) {
                entity.setEmailSentStatus("SENT");
                entity.setSentAt(java.time.Instant.now().toString());
                logger.info("Email sent successfully to {}", entity.getSubscriberEmail());
            } else {
                entity.setEmailSentStatus("FAILED");
                logger.error("Failed to send email to {}", entity.getSubscriberEmail());
            }
        } catch (Exception ex) {
            entity.setEmailSentStatus("FAILED");
            logger.error("Exception while sending email to {}: {}", entity.getSubscriberEmail(), ex.getMessage(), ex);
        }

        // NOTE: The entity is updated in-place and will be persisted automatically
        return entity;
    }

    private boolean sendEmail(String toEmail, String notificationDate) {
        logger.info("Sending email to {} with NBA scores for date {}", toEmail, notificationDate);
        // Simulate email sending logic
        return true;
    }
}
