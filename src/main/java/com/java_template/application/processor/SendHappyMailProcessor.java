package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class SendHappyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendHappyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendHappyMailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendHappyMail for request: {}", request.getId());

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
        // Set status to SENDING_HAPPY (assuming status transition persisted by workflow engine before calling processor,
        // but we set it defensively here too)
        mail.setStatus("SENDING_HAPPY");
        mail.setLastAttemptAt(OffsetDateTime.now());

        try {
            // Simulated sending: iterate recipients and simulate success
            for (String recipient : mail.getMailList()) {
                // For prototype: treat invalid email format as permanent failure
                if (!isValidEmail(recipient)) {
                    logger.error("Permanent failure: invalid email {} for mail {}", recipient, mail.getTechnicalId());
                    mail.setStatus("FAILED");
                    return mail;
                }
                // Simulate send success (in real implementation call mail service)
                logger.info("Sending happy mail to {} for mail {}", recipient, mail.getTechnicalId());
            }

            // All recipients succeeded
            mail.setStatus("SENT");
            return mail;
        } catch (RuntimeException ex) {
            logger.error("Transient error sending happy mail {}: {}", mail.getTechnicalId(), ex.getMessage());
            // Transient failure handling
            Integer attempts = mail.getAttemptCount();
            if (attempts == null) attempts = 0;
            attempts = attempts + 1;
            mail.setAttemptCount(attempts);
            mail.setLastAttemptAt(OffsetDateTime.now());
            if (attempts < 3) { // MAX_RETRIES default 3
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
