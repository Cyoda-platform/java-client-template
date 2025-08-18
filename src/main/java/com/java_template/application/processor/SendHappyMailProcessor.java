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
public class SendHappyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendHappyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private static final int MAX_RETRIES = 3;
    private static final String STATUS_SENDING = "SENDING_HAPPY";
    private static final String STATUS_READY = "READY_TO_SEND";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";

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

        // Defensive defaults
        if (mail.getAttemptCount() == null) mail.setAttemptCount(0);

        // Ensure we don't accidentally process mails that are already final
        String currentStatus = mail.getStatus();
        if (STATUS_SENT.equals(currentStatus) || STATUS_FAILED.equals(currentStatus)) {
            logger.warn("Mail {} is in terminal state {}. Skipping processing.", mail.getTechnicalId(), currentStatus);
            return mail;
        }

        // Transition to sending
        mail.setStatus(STATUS_SENDING);
        mail.setLastAttemptAt(OffsetDateTime.now());

        try {
            // For this iteration, sending is atomic for the whole mail. If any recipient causes a permanent failure,
            // mark the whole Mail as FAILED. If a transient problem occurs, increase attemptCount and move back to READY_TO_SEND

            for (String recipient : mail.getMailList()) {
                if (!isValidEmail(recipient)) {
                    logger.error("Permanent failure: invalid email {} for mail {}", recipient, mail.getTechnicalId());
                    mail.setStatus(STATUS_FAILED);
                    return mail;
                }
                // Simulate send - in real implementation call a mail service with idempotency headers
                logger.info("[HAPPY] Sending to {} for mail {}", recipient, mail.getTechnicalId());
            }

            // All recipients succeeded
            mail.setStatus(STATUS_SENT);
            return mail;
        } catch (RuntimeException ex) {
            // Treat runtime exceptions as transient unless classified otherwise
            logger.error("Transient error sending happy mail {}: {}", mail.getTechnicalId(), ex.getMessage());
            Integer attempts = mail.getAttemptCount();
            if (attempts == null) attempts = 0;
            attempts = attempts + 1;
            mail.setAttemptCount(attempts);
            mail.setLastAttemptAt(OffsetDateTime.now());
            if (attempts < MAX_RETRIES) {
                mail.setStatus(STATUS_READY);
            } else {
                mail.setStatus(STATUS_FAILED);
            }
            return mail;
        }
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        return email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
