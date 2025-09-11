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
 * MailSendGloomyMailProcessor - Processes the sending of gloomy/sad emails
 * 
 * This processor handles the sending of gloomy/sad email content to all recipients
 * in the mail list when the mail entity has isHappy = false and is in GLOOMY_READY state.
 */
@Component
public class MailSendGloomyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MailSendGloomyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MailSendGloomyMailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing gloomy mail for request: {}", request.getId());

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
     * Checks that the entity and metadata are valid and that the mail is gloomy
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
        if (!"GLOOMY_READY".equals(currentState)) {
            logger.warn("Mail entity is not in GLOOMY_READY state, current state: {}", currentState);
            return false;
        }

        // Check if mail is gloomy (not happy)
        if (entity.getIsHappy() == null || entity.getIsHappy()) {
            logger.warn("Mail entity is not gloomy: {}", entity.getIsHappy());
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
     * Main business logic for sending gloomy emails
     * Sends gloomy/sad email content to all recipients in the mail list
     */
    private EntityWithMetadata<Mail> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Mail> context) {

        EntityWithMetadata<Mail> entityWithMetadata = context.entityResponse();
        Mail entity = entityWithMetadata.entity();

        // Get current entity metadata
        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing gloomy mail entity: {} in state: {}", currentEntityId, currentState);

        // Send gloomy emails to all recipients
        sendGloomyEmails(entity);

        logger.info("Gloomy mail {} processed successfully, sent to {} recipients", 
                   currentEntityId, entity.getMailList().size());

        // Return the entity unchanged (processor cannot modify current entity)
        return entityWithMetadata;
    }

    /**
     * Sends gloomy email content to all recipients in the mail list
     */
    private void sendGloomyEmails(Mail entity) {
        String subject = "💔 Gloomy Mail - Sharing the Blues";
        String gloomyContent = generateGloomyEmailContent();

        for (String emailAddress : entity.getMailList()) {
            try {
                // Simulate email sending (in real implementation, this would call an email service)
                sendEmail(emailAddress, subject, gloomyContent);
                logger.debug("Gloomy email sent successfully to: {}", emailAddress);
            } catch (Exception e) {
                logger.error("Failed to send gloomy email to: {}", emailAddress, e);
                // Continue processing remaining emails even if individual sends fail
            }
        }
    }

    /**
     * Generates melancholic/sad message content for gloomy emails
     */
    private String generateGloomyEmailContent() {
        return """
            Dear Friend,
            
            Sometimes life feels heavy, and that's okay. 💙
            
            We're sharing this moment of reflection with you:
            
            🌧️ It's natural to feel sad sometimes
            🌙 Even in darkness, there's beauty to be found
            💧 Tears can be healing and cleansing
            🍂 Every ending makes space for new beginnings
            
            Remember that it's okay to not be okay. Take your time,
            be gentle with yourself, and know that brighter days will come.
            
            You're not alone in this feeling.
            
            With understanding and care,
            The Gloomy Mail Team
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
        
        logger.info("📧 GLOOMY EMAIL SENT:");
        logger.info("   To: {}", toAddress);
        logger.info("   Subject: {}", subject);
        logger.info("   Content: [Gloomy email content sent]");
        
        // Simulate some processing time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
