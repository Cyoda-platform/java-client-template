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
        String technicalId = context.request().getEntityId();

        logger.info("Processing Mail entity with technicalId: {}", technicalId);
        try {
            boolean criteriaResult = mail.isHappy();
            if (criteriaResult) {
                sendHappyMail(mail);
                mail.setStatus("COMPLETED");
                logger.info("Happy mail sent for technicalId: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                mail.setStatus("COMPLETED");
                logger.info("Gloomy mail sent for technicalId: {}", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to process mail with technicalId: {}. Error: {}", technicalId, e.getMessage());
        }

        return mail;
    }

    private void sendHappyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending HAPPY mail to {}", recipient);
        }
    }

    private void sendGloomyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending GLOOMY mail to {}", recipient);
        }
    }
}
