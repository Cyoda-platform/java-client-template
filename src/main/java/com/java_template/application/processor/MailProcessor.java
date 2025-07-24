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
import java.util.List;

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
        logger.info("Processing Mail with current status: {}", mail.getStatus());

        boolean isHappyCriteria = checkMailIsHappy(mail);
        boolean isGloomyCriteria = checkMailIsGloomy(mail);

        if (isHappyCriteria) {
            sendHappyMail(mail);
        } else if (isGloomyCriteria) {
            sendGloomyMail(mail);
        } else {
            logger.error("Mail does not meet happy or gloomy criteria");
            mail.setStatus("FAILED");
            return mail;
        }

        mail.setStatus("SENT");
        logger.info("Mail processed and sent successfully");
        return mail;
    }

    private boolean checkMailIsHappy(Mail mail) {
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailIsGloomy(Mail mail) {
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        logger.info("Sending happy mail to recipients: {}", mail.getMailList());
        // Simulate sending happy mail content here
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // Simulate sending gloomy mail content here
    }
}
