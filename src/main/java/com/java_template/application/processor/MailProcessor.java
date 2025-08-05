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
import com.fasterxml.jackson.databind.ObjectMapper;

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
        Mail entity = context.entity();
        String technicalId = context.request().getEntityId();

        // Validation: check mailList not empty and content not blank
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.error("Mail list is empty for mail id {}", technicalId);
            throw new IllegalArgumentException("mailList cannot be empty");
        }
        if (entity.getContent() == null || entity.getContent().isBlank()) {
            logger.error("Mail content is blank for mail id {}", technicalId);
            throw new IllegalArgumentException("content cannot be blank");
        }

        // Processing: send happy or gloomy mail based on isHappy flag
        if (entity.isHappy()) {
            sendHappyMail(technicalId, entity);
        } else {
            sendGloomyMail(technicalId, entity);
        }

        // Completion: persist sending status if needed (simulate here)
        logger.info("Mail with id {} sent successfully. isHappy={}", technicalId, entity.isHappy());

        return entity;
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            // Simulate sending happy mail
            logger.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
        }
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            // Simulate sending gloomy mail
            logger.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
        }
    }

}
