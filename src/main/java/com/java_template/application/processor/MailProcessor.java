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
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Mail.class)
                .validate(Mail::isValid)
                .map(this::processMailLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
                "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Mail processMailLogic(Mail mail) {
        logger.info("Processing Mail with ID: {}", mail.getId());

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            processMailSendHappyMail(mail);
        } else if (Boolean.FALSE.equals(mail.getIsHappy())) {
            processMailSendGloomyMail(mail);
        } else {
            logger.error("Mail with ID {} does not meet any criteria for processing", mail.getId());
        }

        return mail;
    }

    private void processMailSendHappyMail(Mail mail) {
        logger.info("Sending HAPPY mail to recipients: {} for Mail ID: {}", mail.getMailList(), mail.getId());

        for (String recipient : mail.getMailList()) {
            logger.info("Happy mail sent to {}", recipient);
        }
    }

    private void processMailSendGloomyMail(Mail mail) {
        logger.info("Sending GLOOMY mail to recipients: {} for Mail ID: {}", mail.getMailList(), mail.getId());

        for (String recipient : mail.getMailList()) {
            logger.info("Gloomy mail sent to {}", recipient);
        }
    }
}
