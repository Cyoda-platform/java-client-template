package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
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
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("MailProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Mail.class)
                .validate(this::isValidEntity, "Invalid Mail entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.isValid();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        String technicalId = context.request().getEntityId();

        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            logger.error("Mail ID {} has empty mailList", technicalId);
            mail.setStatus("FAILED");
            return mail;
        }

        if (mail.getContent() == null || mail.getContent().isBlank()) {
            logger.error("Mail ID {} has empty content", technicalId);
            mail.setStatus("FAILED");
            return mail;
        }

        if (mail.getIsHappy() == null) {
            logger.error("Mail ID {} has null isHappy field", technicalId);
            mail.setStatus("FAILED");
            return mail;
        }

        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(technicalId, mail);
                mail.setStatus("SENT_HAPPY");
                logger.info("Mail ID {} sent as happy mail", technicalId);
            } else {
                sendGloomyMail(technicalId, mail);
                mail.setStatus("SENT_GLOOMY");
                logger.info("Mail ID {} sent as gloomy mail", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Error processing Mail ID {}: {}", technicalId, e.getMessage());
        }

        return mail;
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        logger.info("Sending happy mail to recipients: {}", mail.getMailList());
        // Actual sending mechanism can be integrated here
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        logger.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // Actual sending mechanism can be integrated here
    }
}
