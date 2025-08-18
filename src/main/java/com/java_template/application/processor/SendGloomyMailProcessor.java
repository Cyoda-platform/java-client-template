package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
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

import java.time.OffsetDateTime;

@Component
public class SendGloomyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendGloomyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendGloomyMailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendGloomyMail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid Mail entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail mail) {
        if (mail == null) return false;
        return mail.getMailList() != null && !mail.getMailList().isEmpty();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        mail.setStatus("SENDING_GLOOMY");
        mail.setLastAttemptAt(OffsetDateTime.now());

        try {
            for (String recipient : mail.getMailList()) {
                if (!isValidEmail(recipient)) {
                    logger.error("Permanent failure: invalid email {} for mail {}", recipient, mail.getTechnicalId());
                    mail.setStatus("FAILED");
                    return mail;
                }
                logger.info("Sending gloomy mail to {} for mail {}", recipient, mail.getTechnicalId());
            }
            mail.setStatus("SENT");
            return mail;
        } catch (RuntimeException ex) {
            logger.error("Transient error sending gloomy mail {}: {}", mail.getTechnicalId(), ex.getMessage());
            Integer attempts = mail.getAttemptCount();
            if (attempts == null) attempts = 0;
            attempts = attempts + 1;
            mail.setAttemptCount(attempts);
            mail.setLastAttemptAt(OffsetDateTime.now());
            if (attempts < 3) {
                mail.setStatus("READY_TO_SEND");
            } else {
                mail.setStatus("FAILED");
            }
            return mail;
        }
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        return email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
