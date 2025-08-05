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
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MailProcessor(SerializerFactory serializerFactory) {
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
        Mail mail = context.entity();

        // Using the business logic from CyodaEntityControllerPrototype processMail method
        try {
            logger.info("Processing Mail with technicalId={}", context.request().getEntityId());

            if (mail.getIsHappy() != null && mail.getIsHappy()) {
                sendHappyMail(mail);
            } else {
                sendGloomyMail(mail);
            }
        } catch (Exception e) {
            logger.error("Exception in MailProcessor for technicalId={}", context.request().getEntityId(), e);
        }

        return mail;
    }

    private void sendHappyMail(Mail mail) {
        logger.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Simulate sending mails
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Simulate sending mails
    }
}
