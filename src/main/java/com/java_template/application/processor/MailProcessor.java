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

        // Predefined chain, business logic in processEntityLogic
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

        try {
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Mail with technicalId {} has empty mailList", technicalId);
                return mail;
            }
            if (mail.getContent() == null || mail.getContent().isBlank()) {
                logger.error("Mail with technicalId {} has empty content", technicalId);
                return mail;
            }

            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(technicalId, mail);
            } else if (Boolean.FALSE.equals(mail.getIsHappy())) {
                sendGloomyMail(technicalId, mail);
            } else {
                logger.info("Mail with technicalId {} has undefined isHappy status", technicalId);
            }
        } catch (Exception e) {
            logger.error("Exception in processMail for technicalId {}", technicalId, e);
        }

        return mail;
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
            // Real mail sending logic with JavaMail or external API would go here
        }
        logger.info("Completed sending happy mail for technicalId {}", technicalId);
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
            // Real mail sending logic with JavaMail or external API would go here
        }
        logger.info("Completed sending gloomy mail for technicalId {}", technicalId);
    }
}
