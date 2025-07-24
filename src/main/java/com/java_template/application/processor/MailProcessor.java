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

import java.util.concurrent.CompletableFuture;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public MailProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
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
        // Since the original prototype method requires technicalId which is not part of the entity,
        // we will simulate by extracting it from context or logging without it.
        // But the event request id can be used as technicalId representation in logging.

        // We cannot get technicalId directly here as we only have the entity.
        // So replicate the logic without technicalId parameter.

        if (mail.getMailList() == null || mail.getMailList().isEmpty() || mail.getIsHappy() == null) {
            logger.error("Invalid mail data detected in processor");
            mail.setStatus("FAILED");
            addUpdatedMailEntity(mail);
            return mail;
        }

        try {
            if (mail.getIsHappy()) {
                sendHappyMail(mail);
            } else {
                sendGloomyMail(mail);
            }
            mail.setStatus("SENT");
            logger.info("Mail sent successfully");
        } catch (Exception e) {
            logger.error("Failed to send mail: {}", e.getMessage());
            mail.setStatus("FAILED");
        }

        addUpdatedMailEntity(mail);
        return mail;
    }

    private void addUpdatedMailEntity(Mail mail) {
        try {
            // The prototype method added previousTechnicalId which is not present in entity POJO, so omitted.
            CompletableFuture<java.util.UUID> updateFuture = entityService.addItem(
                    "Mail",
                    Config.ENTITY_VERSION,
                    mail
            );

            updateFuture.join();

        } catch (Exception e) {
            logger.error("Failed to add updated Mail entity: {}", e.getMessage());
        }
    }

    private void sendHappyMail(Mail mail) {
        logger.info("sendHappyMail processor invoked for mailList: {}", mail.getMailList());
        // Simulate sending happy mail - real implementation would send emails here
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("sendGloomyMail processor invoked for mailList: {}", mail.getMailList());
        // Simulate sending gloomy mail - real implementation would send emails here
    }
}
