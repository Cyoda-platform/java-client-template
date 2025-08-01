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

@Component
public class processMail implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public processMail(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

        logger.info("processMail: Processing Mail entity with technicalId: {}", technicalId);

        if (entity.isHappy()) {
            processMailSendHappyMail(technicalId, entity);
        } else {
            processMailSendGloomyMail(technicalId, entity);
        }

        return entity;
    }

    private void processMailSendHappyMail(String technicalId, Mail mail) {
        logger.info("Sending Happy Mail for technicalId: {}", technicalId);
        for (String recipient : mail.getMailList()) {
            logger.info("Happy mail sent to: {} with subject: {}", recipient, mail.getSubject());
        }
        logger.info("Completed sending Happy Mail for technicalId: {}", technicalId);
    }

    private void processMailSendGloomyMail(String technicalId, Mail mail) {
        logger.info("Sending Gloomy Mail for technicalId: {}", technicalId);
        for (String recipient : mail.getMailList()) {
            logger.info("Gloomy mail sent to: {} with subject: {}", recipient, mail.getSubject());
        }
        logger.info("Completed sending Gloomy Mail for technicalId: {}", technicalId);
    }
}
