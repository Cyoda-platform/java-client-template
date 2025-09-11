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
 * MailSendGloomyMailProcessor - Processes and sends gloomy mail messages
 * 
 * This processor handles the business logic for sending somber, melancholic email
 * content to all recipients in the mail list when isHappy = false.
 * 
 * Expected input: Mail entity with isHappy = false and populated mailList
 * Expected state: GLOOMY_PROCESSING
 * Output: No transition needed (null transition) - workflow handles state automatically
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
                .validate(this::isValidEntityWithMetadata, "Invalid mail entity for gloomy processing")
                .map(this::processGloomyMailLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for gloomy mail processing
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Mail> entityWithMetadata) {
        Mail entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        // Validate basic structure
        if (entity == null || !entity.isValid() || technicalId == null) {
            return false;
        }
        
        // Validate state is correct for gloomy processing
        if (!"gloomy_processing".equalsIgnoreCase(currentState)) {
            logger.warn("Mail entity {} is not in GLOOMY_PROCESSING state (current: {})", technicalId, currentState);
            return false;
        }
        
        // Validate this is indeed a gloomy mail
        if (!Boolean.FALSE.equals(entity.getIsHappy())) {
            logger.warn("Mail entity {} is not marked as gloomy (isHappy = {})", technicalId, entity.getIsHappy());
            return false;
        }
        
        return true;
    }

    /**
     * Main business logic for processing gloomy mail
     * 
     * CRITICAL LIMITATIONS:
     * - ✅ ALLOWED: Read current entity data
     * - ❌ FORBIDDEN: Update current entity state/transitions (handled by workflow)
     * - ❌ FORBIDDEN: Update other entities (not needed for this processor)
     */
    private EntityWithMetadata<Mail> processGloomyMailLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Mail> context) {

        EntityWithMetadata<Mail> entityWithMetadata = context.entityResponse();
        Mail entity = entityWithMetadata.entity();
        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing gloomy mail entity: {} in state: {}", currentEntityId, currentState);

        // Extract mail list from entity
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.error("Mail list is empty for entity {}", currentEntityId);
            throw new RuntimeException("Mail list cannot be empty");
        }

        // Prepare gloomy mail content
        String subject = "🌧️ Reflective Thoughts - A Moment of Contemplation";
        String content = generateGloomyMailContent();

        // Send mail to each recipient
        int successCount = 0;
        for (String recipient : entity.getMailList()) {
            try {
                sendEmail(recipient, subject, content, "GLOOMY");
                logger.info("Gloomy mail sent successfully to: {}", recipient);
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to send gloomy mail to: {} - Error: {}", recipient, e.getMessage());
                throw new RuntimeException("Failed to send gloomy mail to " + recipient, e);
            }
        }

        logger.info("All gloomy mails sent successfully to {} recipients for entity {}", 
                   successCount, currentEntityId);

        // Return processed entity (unchanged - workflow handles state transition)
        return entityWithMetadata;
    }

    /**
     * Generates gloomy mail content
     */
    private String generateGloomyMailContent() {
        return """
            Dear Recipient,
            
            Sometimes life brings us moments of reflection and contemplation. 🌧️
            
            In these quiet moments, we might ponder:
            - The fleeting nature of time
            - The weight of our daily struggles
            - The complexity of human emotions
            
            Remember that even in darker moments, there is meaning to be found.
            
            With thoughtful regards,
            The Contemplative Mail Team
            """;
    }

    /**
     * Simulates sending email to external mail service
     * In a real implementation, this would integrate with an actual email service
     */
    private void sendEmail(String recipient, String subject, String content, String type) {
        // Simulate email sending with basic validation
        if (recipient == null || recipient.trim().isEmpty()) {
            throw new RuntimeException("Recipient email cannot be empty");
        }
        
        if (!recipient.contains("@")) {
            throw new RuntimeException("Invalid email format: " + recipient);
        }
        
        // Simulate potential email service failure (uncomment to test error handling)
        // if (recipient.contains("fail")) {
        //     throw new RuntimeException("Simulated email service failure");
        // }
        
        logger.debug("Sending {} email to: {} with subject: {}", type, recipient, subject);
        
        // In real implementation, this would call external email service
        // emailService.send(recipient, subject, content);
    }
}
