package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        // Extract technical ID from request
        String technicalId = context.request().getEntityId();

        // Validate mailList is not empty and contains valid emails
        List<String> mailList = mail.getMailList();
        if (mailList == null || mailList.isEmpty()) {
            logger.error("MailList is empty for Mail entity with technicalId: {}", technicalId);
            return mail;
        }

        boolean allValidEmails = mailList.stream().allMatch(this::isValidEmail);
        if (!allValidEmails) {
            logger.error("MailList contains invalid email addresses for Mail entity with technicalId: {}", technicalId);
            return mail;
        }

        // Depending on isHappy flag, send happy or gloomy mail
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mail);
            logger.info("Sent happy mail for technicalId: {}", technicalId);
        } else {
            sendGloomyMail(mail);
            logger.info("Sent gloomy mail for technicalId: {}", technicalId);
        }

        // After sending mails, update mail entity status or result if needed (not persisted here as immutable)

        return mail;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        // Basic regex for email validation
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private void sendHappyMail(Mail mail) {
        // Replace with actual email sending logic using JavaMailSender or similar
        logger.info("Simulating sending happy mail to: {}", mail.getMailList());
    }

    private void sendGloomyMail(Mail mail) {
        // Replace with actual email sending logic using JavaMailSender or similar
        logger.info("Simulating sending gloomy mail to: {}", mail.getMailList());
    }
}
