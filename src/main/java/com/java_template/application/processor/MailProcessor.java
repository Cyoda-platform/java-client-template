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

        List<String> mailList = mail.getMailList();
        if (mailList == null || mailList.isEmpty()) {
            logger.error("Mail list empty or null for technicalId={}", technicalId);
            return mail;
        }

        boolean invalidEmailFound = mailList.stream().anyMatch(email -> email == null || email.isBlank() || !email.contains("@"));
        if (invalidEmailFound) {
            logger.error("Invalid email address found in mailList for technicalId={}", technicalId);
            return mail;
        }

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mailList, technicalId);
        } else {
            sendGloomyMail(mailList, technicalId);
        }

        logger.info("Finished processing Mail entity technicalId={}", technicalId);

        return mail;
    }

    private void sendHappyMail(List<String> recipients, String technicalId) {
        for (String recipient : recipients) {
            logger.info("Sent HAPPY mail to {} for technicalId={}", recipient, technicalId);
        }
    }

    private void sendGloomyMail(List<String> recipients, String technicalId) {
        for (String recipient : recipients) {
            logger.info("Sent GLOOMY mail to {} for technicalId={}", recipient, technicalId);
        }
    }
}
