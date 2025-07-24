package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public MailProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("MailProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Mail.class)
                .validate(Mail::isValid)
                .map(this::processMailLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
                "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Mail processMailLogic(Mail mail) {
        // Simulate technicalId from request context or generate new
        UUID technicalId = UUID.randomUUID();
        logger.info("Processing Mail with simulated technicalId: {}", technicalId);

        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(mail);
            } else {
                sendGloomyMail(mail);
            }
            mail.setStatus("SENT");
            logger.info("Mail sent successfully");
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to send Mail: {}", e.getMessage());
        }

        // Instead of updating, create a new entity version with reference to previous technicalId
        Mail updatedMail = new Mail();
        updatedMail.setMailList(mail.getMailList());
        updatedMail.setIsHappy(mail.getIsHappy());
        updatedMail.setStatus(mail.getStatus());

        // The existing Mail entity does not have previousTechnicalId property, so this line is omitted

        entityService.addItem(
                "mail",
                Config.ENTITY_VERSION,
                updatedMail
        ).join();

        return mail;
    }

    private void sendHappyMail(Mail mail) {
        logger.info("Sending HAPPY mail to recipients: {}", mail.getMailList());
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("Sending GLOOMY mail to recipients: {}", mail.getMailList());
    }
}
