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

        // Evaluate happiness criteria based on content
        String contentLower = mail.getContent() != null ? mail.getContent().toLowerCase() : "";
        boolean isHappy = contentLower.contains("happy") || contentLower.contains("joy") || contentLower.contains("glad");
        mail.setIsHappy(isHappy);
        logger.info("Mail {} happiness evaluated: {}", technicalId, isHappy);

        // Process sending mail based on happiness
        if (isHappy) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }

        return mail;
    }

    // Helper method to send happy mails
    private void sendHappyMail(String technicalId, Mail mail) {
        logger.info("Sending HAPPY mail to {} with content: {}", mail.getMailList(), mail.getContent());
        // Integration with mail sending service can be done here
        logger.info("Happy mail sent successfully for technicalId {}", technicalId);
    }

    // Helper method to send gloomy mails
    private void sendGloomyMail(String technicalId, Mail mail) {
        logger.info("Sending GLOOMY mail to {} with content: {}", mail.getMailList(), mail.getContent());
        // Integration with mail sending service can be done here
        logger.info("Gloomy mail sent successfully for technicalId {}", technicalId);
    }
}
