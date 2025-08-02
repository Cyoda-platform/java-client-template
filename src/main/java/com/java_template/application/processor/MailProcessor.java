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
        // Business logic from processMail method in prototype
        try {
            if (checkMailHappyCriteria(mail) && Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(mail);
                logger.info("Happy mail sent for requestId: {}", context.request().getId());
            } else if (checkMailGloomyCriteria(mail) && Boolean.FALSE.equals(mail.getIsHappy())) {
                sendGloomyMail(mail);
                logger.info("Gloomy mail sent for requestId: {}", context.request().getId());
            } else {
                logger.info("Mail with requestId {} does not meet any sending criteria", context.request().getId());
            }
        } catch (Exception e) {
            logger.error("Failed to send mail for requestId: {}", context.request().getId(), e);
        }
        return mail;
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // As in prototype: happy criteria if isHappy == true
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // As in prototype: gloomy criteria if isHappy == false
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        logger.info("Sending happy mail to recipients: {}", mail.getMailList());
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        logger.info("Sending gloomy mail to recipients: {}", mail.getMailList());
    }
}
