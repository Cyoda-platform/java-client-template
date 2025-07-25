package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
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
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public MailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("MailProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail entity for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(Mail::isValid, "Invalid mail entity state")
            .map(this::processMail)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
               "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Process Mail entity with business logic from prototype
    private Mail processMail(Mail mail) {
        logger.info("Processing Mail entity with mailList size: {}", mail.getMailList().size());

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            if (checkMailHappy(mail)) {
                sendHappyMail(mail);
            } else {
                logger.error("Mail did not pass happy mail criteria");
            }
        } else {
            if (checkMailGloomy(mail)) {
                sendGloomyMail(mail);
            } else {
                logger.error("Mail did not pass gloomy mail criteria");
            }
        }

        return mail;
    }

    private boolean checkMailHappy(Mail mail) {
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        logger.info("Sending HAPPY mail to recipients: {}", mail.getMailList());
        // Business logic like sending email, updating status, notifications, etc.
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("Sending GLOOMY mail to recipients: {}", mail.getMailList());
        // Business logic like sending email, updating status, notifications, etc.
    }
}
