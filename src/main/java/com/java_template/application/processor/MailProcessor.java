package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public MailProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.isValid();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        String technicalId = context.request().getEntityId();

        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            logger.error("Mail {} has empty mailList", technicalId);
            mail.setStatus("FAILED");
            return mail;
        }

        List<String> mailList = mail.getMailList();
        for (String email : mailList) {
            if (email == null || email.isBlank()) {
                logger.error("Mail {} has invalid email in mailList", technicalId);
                mail.setStatus("FAILED");
                return mail;
            }
        }

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mailList, technicalId);
        } else {
            sendGloomyMail(mailList, technicalId);
        }

        return mail;
    }

    private void sendHappyMail(List<String> mailList, String technicalId) {
        String subject = "Happy Greetings!";
        String content = "Wishing you a joyful and happy day!";
        boolean success = sendEmails(mailList, subject, content);
        if (success) {
            logger.info("Happy mail sent successfully for technicalId {}", technicalId);
        } else {
            logger.error("Failed to send happy mail for technicalId {}", technicalId);
        }
    }

    private void sendGloomyMail(List<String> mailList, String technicalId) {
        String subject = "Thoughtful Reflections";
        String content = "Sometimes, a quiet moment is needed to reflect.";
        boolean success = sendEmails(mailList, subject, content);
        if (success) {
            logger.info("Gloomy mail sent successfully for technicalId {}", technicalId);
        } else {
            logger.error("Failed to send gloomy mail for technicalId {}", technicalId);
        }
    }

    // Dummy sendEmails method to simulate sending mails
    private boolean sendEmails(List<String> mailList, String subject, String content) {
        // Here you would integrate with actual mail sending service
        // For prototype, just log and return true
        logger.info("Sending mail to: {} with subject: {} and content: {}", mailList, subject, content);
        return true;
    }
}
