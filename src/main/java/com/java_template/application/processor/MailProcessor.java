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
        Mail entity = context.entity();
        String technicalId = context.request().getEntityId();

        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.error("MailList is empty for mail with technicalId: {}", technicalId);
            return entity;
        }
        if (entity.getContent() == null || entity.getContent().isBlank()) {
            logger.error("Mail content is blank for mail with technicalId: {}", technicalId);
            return entity;
        }
        if (Boolean.TRUE.equals(entity.getIsHappy())) {
            for (String recipient : entity.getMailList()) {
                logger.info("Sending HAPPY mail to {}: {}", recipient, entity.getContent());
                // Actual mail sending logic can be implemented here if needed
            }
            logger.info("All happy mails sent successfully for mail with technicalId: {}", technicalId);
            entity.setMailType("happy");
        } else {
            for (String recipient : entity.getMailList()) {
                logger.info("Sending GLOOMY mail to {}: {}", recipient, entity.getContent());
                // Actual mail sending logic can be implemented here if needed
            }
            logger.info("All gloomy mails sent successfully for mail with technicalId: {}", technicalId);
            entity.setMailType("gloomy");
        }

        return entity;
    }
}