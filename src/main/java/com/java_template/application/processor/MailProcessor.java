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
        String technicalId = context.request().getEntityId();

        boolean happyCriteriaMet = checkMailHappy(mail);
        boolean gloomyCriteriaMet = checkMailGloomy(mail);

        if (happyCriteriaMet) {
            sendHappyMail(technicalId, mail);
        } else if (gloomyCriteriaMet) {
            sendGloomyMail(technicalId, mail);
        } else {
            logger.error("Mail entity with technicalId {} failed criteria check", technicalId);
        }

        return mail;
    }

    private boolean checkMailHappy(Mail mail) {
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        logger.info("Sending happy mail for technicalId: {}, to recipients: {}", technicalId, mail.getMailList());
        // Implement sending happy mail logic or integrate with external mail service
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        logger.info("Sending gloomy mail for technicalId: {}, to recipients: {}", technicalId, mail.getMailList());
        // Implement sending gloomy mail logic or integrate with external mail service
    }
}
