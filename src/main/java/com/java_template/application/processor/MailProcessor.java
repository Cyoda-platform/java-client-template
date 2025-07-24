package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

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

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(Mail::isValid)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
               "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Mail processEntityLogic(Mail mail) {
        try {
            UUID technicalId = null;
            // Attempt to extract technicalId from mail's internal state or request context if needed
            // Here we use a dummy UUID for logging since no direct getter for technicalId in Mail
            // In real scenario, technicalId should come from request or entity metadata
            technicalId = UUID.randomUUID();

            logger.info("Processing Mail with ID: {}", technicalId);

            boolean happyCriteriaMet = checkMailHappyCriteria(mail);
            boolean gloomyCriteriaMet = checkMailGloomyCriteria(mail);

            if (happyCriteriaMet) {
                mail.setIsHappy(true);
                mail.setStatus("PROCESSING");
                logger.info("Mail ID {} classified as Happy", technicalId);
                sendHappyMail(technicalId, mail);
            } else if (gloomyCriteriaMet) {
                mail.setIsHappy(false);
                mail.setStatus("PROCESSING");
                logger.info("Mail ID {} classified as Gloomy", technicalId);
                sendGloomyMail(technicalId, mail);
            } else {
                mail.setIsHappy(false);
                mail.setStatus("PROCESSING");
                logger.info("Mail ID {} did not meet happy criteria, classified as Gloomy by default", technicalId);
                sendGloomyMail(technicalId, mail);
            }
        } catch (Exception e) {
            logger.error("Error processing mail: {}", e.getMessage(), e);
            mail.setStatus("FAILED");
        }
        return mail;
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        return mail.getMailList() != null && mail.getMailList().size() % 2 == 0;
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        return mail.getMailList() != null && mail.getMailList().size() % 2 != 0;
    }

    private void sendHappyMail(UUID technicalId, Mail mail) {
        try {
            logger.info("Sending Happy Mail to: {}", mail.getMailList());
            mail.setStatus("SENT");
            logger.info("Happy Mail sent successfully for ID: {}", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to send Happy Mail for ID: {}, error: {}", technicalId, e.getMessage());
        }
    }

    private void sendGloomyMail(UUID technicalId, Mail mail) {
        try {
            logger.info("Sending Gloomy Mail to: {}", mail.getMailList());
            mail.setStatus("SENT");
            logger.info("Gloomy Mail sent successfully for ID: {}", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to send Gloomy Mail for ID: {}, error: {}", technicalId, e.getMessage());
        }
    }

}
