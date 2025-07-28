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
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.isValid();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        String technicalId = context.request().getEntityId();

        // Validation double-check
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            logger.error("Mail list is empty, cannot process mail with technicalId: {}", technicalId);
            return mail;
        }

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }

        return mail;
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        try {
            for (String recipient : mail.getMailList()) {
                logger.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
                // Actual email sending logic would be here
            }
            logger.info("Successfully sent HAPPY mail for technicalId: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to send HAPPY mail for technicalId: {} due to {}", technicalId, e.getMessage());
        }
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        try {
            for (String recipient : mail.getMailList()) {
                logger.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
                // Actual email sending logic would be here
            }
            logger.info("Successfully sent GLOOMY mail for technicalId: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to send GLOOMY mail for technicalId: {} due to {}", technicalId, e.getMessage());
        }
    }

}
