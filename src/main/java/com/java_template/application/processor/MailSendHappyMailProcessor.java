package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * MailSendHappyMailProcessor - Processes the sending of happy/positive emails
 * 
 * This processor handles the sending of happy/positive email content to all recipients
 * in the mail list when the mail entity has isHappy = true and is in HAPPY_READY state.
 */
@Component
public class MailSendHappyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MailSendHappyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MailSendHappyMailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing happy mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Mail.class)
                .validate(this::isValidEntityWithMetadata, "Invalid mail entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     * Checks that the entity and metadata are valid and that the mail is happy
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Mail> entityWithMetadata) {
        Mail entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        // Basic validation
        if (entity == null || !entity.isValid() || technicalId == null) {
            logger.warn("Basic validation failed for mail entity");
            return false;
        }

        // Check if entity is in correct state
        if (!"HAPPY_READY".equals(currentState)) {
            logger.warn("Mail entity is not in HAPPY_READY state, current state: {}", currentState);
            return false;
        }

        // Check if mail is happy
        if (entity.getIsHappy() == null || !entity.getIsHappy()) {
            logger.warn("Mail entity is not happy: {}", entity.getIsHappy());
            return false;
        }

        // Check if mailList is valid
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.warn("Mail entity has empty mail list");
            return false;
        }

        return true;
    }

    /**
     * Main business logic for sending happy emails
     * Sends happy/positive email content to all recipients in the mail list
     */
    private EntityWithMetadata<Mail> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Mail> context) {

        EntityWithMetadata<Mail> entityWithMetadata = context.entityResponse();
        Mail entity = entityWithMetadata.entity();

        // Get current entity metadata
        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing happy mail entity: {} in state: {}", currentEntityId, currentState);

        // Send happy emails to all recipients
        sendHappyEmails(entity);

        logger.info("Happy mail {} processed successfully, sent to {} recipients", 
                   currentEntityId, entity.getMailList().size());

        // Return the entity unchanged (processor cannot modify current entity)
        return entityWithMetadata;
    }

    /**
     * Sends happy email content to all recipients in the mail list
     */
    private void sendHappyEmails(Mail entity) {
        String subject = "🌟 Happy Mail - Brighten Your Day!";
        String happyContent = generateHappyEmailContent();

        for (String emailAddress : entity.getMailList()) {
            try {
                // Simulate email sending (in real implementation, this would call an email service)
                sendEmail(emailAddress, subject, happyContent);
                logger.debug("Happy email sent successfully to: {}", emailAddress);
            } catch (Exception e) {
                logger.error("Failed to send happy email to: {}", emailAddress, e);
                // Continue processing remaining emails even if individual sends fail
            }
        }
    }

    /**
     * Generates positive/uplifting message content for happy emails
     */
    private String generateHappyEmailContent() {
        return """
            Dear Friend,
            
            We hope this message brings a smile to your face! 😊
            
            Here's a little sunshine to brighten your day:
            
            ✨ Remember that every day is a new opportunity for happiness
            🌈 Your positive energy makes the world a better place
            🌟 You are capable of amazing things
            💫 Keep shining your light on others
            
            Wishing you joy, laughter, and wonderful moments ahead!
            
            With warm regards,
            The Happy Mail Team
            """;
    }

    /**
     * Simulates sending an email (placeholder for actual email service integration)
     */
    private void sendEmail(String toAddress, String subject, String content) {
        // In a real implementation, this would integrate with an email service like:
        // - SendGrid
        // - Amazon SES
        // - SMTP server
        // - etc.
        
        logger.info("📧 HAPPY EMAIL SENT:");
        logger.info("   To: {}", toAddress);
        logger.info("   Subject: {}", subject);
        logger.info("   Content: [Happy email content sent]");
        
        // Simulate some processing time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
