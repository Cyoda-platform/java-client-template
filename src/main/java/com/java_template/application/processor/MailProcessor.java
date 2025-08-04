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

import java.util.concurrent.CompletableFuture;
import java.util.List;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public MailProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Mail.class)
                .validate(this::isValidEntity, "Invalid mail state")
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

        // Business logic derived from functional requirement and workflow
        // Initial status set as PENDING if not already set
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("PENDING");
        }

        // Process sending mails based on isHappy flag
        if (entity.isHappy()) {
            sendHappyMail(entity);
        } else {
            sendGloomyMail(entity);
        }

        return entity;
    }

    private void sendHappyMail(Mail mail) {
        List<String> recipients = mail.getMailList();
        String content = mail.getContent();

        // Simulate sending happy mail logic
        try {
            for (String recipient : recipients) {
                logger.info("Sending happy mail to: {} with content: {}", recipient, content);
                // Here could be actual sending logic or external service call
            }
            mail.setStatus("SENT");
        } catch (Exception e) {
            logger.error("Failed to send happy mail", e);
            mail.setStatus("FAILED");
        }
    }

    private void sendGloomyMail(Mail mail) {
        List<String> recipients = mail.getMailList();
        String content = mail.getContent();

        // Simulate sending gloomy mail logic
        try {
            for (String recipient : recipients) {
                logger.info("Sending gloomy mail to: {} with content: {}", recipient, content);
                // Here could be actual sending logic or external service call
            }
            mail.setStatus("SENT");
        } catch (Exception e) {
            logger.error("Failed to send gloomy mail", e);
            mail.setStatus("FAILED");
        }
    }
}
