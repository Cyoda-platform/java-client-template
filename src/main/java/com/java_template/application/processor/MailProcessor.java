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
                .map(this::processMail)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
               "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Mail processMail(Mail mail) {
        logger.info("Processing Mail with technicalId: {}", mail.getTechnicalId());

        boolean happyCriteriaMet = checkEntityIsHappy(mail);
        boolean gloomyCriteriaMet = checkEntityIsGloomy(mail);

        if (happyCriteriaMet) {
            mail.setIsHappy(true);
            processMailSendHappyMail(mail);
            mail.setStatus("SENT_HAPPY");
            logger.info("Mail technicalId {} sent as Happy", mail.getTechnicalId());
        } else if (gloomyCriteriaMet) {
            mail.setIsHappy(false);
            processMailSendGloomyMail(mail);
            mail.setStatus("SENT_GLOOMY");
            logger.info("Mail technicalId {} sent as Gloomy", mail.getTechnicalId());
        } else {
            mail.setStatus("FAILED");
            logger.error("Mail technicalId {} does not meet any criteria for sending", mail.getTechnicalId());
            throw new IllegalStateException("Mail does not meet happy or gloomy criteria");
        }

        return mail;
    }

    private boolean checkEntityIsHappy(Mail mail) {
        // Implement actual happy criteria logic here
        // Since criteria are ignored, return false
        return false;
    }

    private boolean checkEntityIsGloomy(Mail mail) {
        // Implement actual gloomy criteria logic here
        // Since criteria are ignored, return false
        return false;
    }

    private void processMailSendHappyMail(Mail mail) {
        logger.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Real implementation would send emails here
    }

    private void processMailSendGloomyMail(Mail mail) {
        logger.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Real implementation would send emails here
    }
}
