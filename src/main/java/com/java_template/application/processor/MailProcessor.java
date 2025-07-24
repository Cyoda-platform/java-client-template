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
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processMail)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
               "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(Mail mail) {
        return mail.isValid();
    }

    private Mail processMail(Mail mail) {
        logger.info("Processing Mail with technicalId: {}", mail.getTechnicalId());

        if (checkMailIsHappy(mail)) {
            sendHappyMail(mail);
        } else if (checkMailIsGloomy(mail)) {
            sendGloomyMail(mail);
        } else {
            logger.error("Mail with technicalId {} does not meet happy or gloomy criteria", mail.getTechnicalId());
            mail.setStatus("FAILED");
        }

        return mail;
    }

    private boolean checkMailIsHappy(Mail mail) {
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailIsGloomy(Mail mail) {
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        logger.info("Sending happy mail to {}", mail.getMailList());
        mail.setStatus("SENT_HAPPY");
        logger.info("Happy mail sent for Mail technicalId: {}", mail.getTechnicalId());
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("Sending gloomy mail to {}", mail.getMailList());
        mail.setStatus("SENT_GLOOMY");
        logger.info("Gloomy mail sent for Mail technicalId: {}", mail.getTechnicalId());
    }
}
