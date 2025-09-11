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
 * MailSendHappyMailProcessor - Processes and sends happy mail messages
 * 
 * This processor handles the business logic for sending cheerful, positive email
 * content to all recipients in the mail list when isHappy = true.
 * 
 * Expected input: Mail entity with isHappy = true and populated mailList
 * Expected state: HAPPY_PROCESSING
 * Output: No transition needed (null transition) - workflow handles state automatically
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
                .validate(this::isValidEntityWithMetadata, "Invalid mail entity for happy processing")
                .map(this::processHappyMailLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for happy mail processing
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Mail> entityWithMetadata) {
        Mail entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        // Validate basic structure
        if (entity == null || !entity.isValid() || technicalId == null) {
            return false;
        }
        
        // Validate state is correct for happy processing
        if (!"happy_processing".equalsIgnoreCase(currentState)) {
            logger.warn("Mail entity {} is not in HAPPY_PROCESSING state (current: {})", technicalId, currentState);
            return false;
        }
        
        // Validate this is indeed a happy mail
        if (!Boolean.TRUE.equals(entity.getIsHappy())) {
            logger.warn("Mail entity {} is not marked as happy (isHappy = {})", technicalId, entity.getIsHappy());
            return false;
        }
        
        return true;
    }

    /**
     * Main business logic for processing happy mail
     * 
     * CRITICAL LIMITATIONS:
     * - ✅ ALLOWED: Read current entity data
     * - ❌ FORBIDDEN: Update current entity state/transitions (handled by workflow)
     * - ❌ FORBIDDEN: Update other entities (not needed for this processor)
     */
    private EntityWithMetadata<Mail> processHappyMailLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Mail> context) {

        EntityWithMetadata<Mail> entityWithMetadata = context.entityResponse();
        Mail entity = entityWithMetadata.entity();
        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing happy mail entity: {} in state: {}", currentEntityId, currentState);

        // Extract mail list from entity
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.error("Mail list is empty for entity {}", currentEntityId);
            throw new RuntimeException("Mail list cannot be empty");
        }

        // Prepare happy mail content
        String subject = "🌟 Happy Mail - Brighten Your Day!";
        String content = generateHappyMailContent();

        // Send mail to each recipient
        int successCount = 0;
        for (String recipient : entity.getMailList()) {
            try {
                sendEmail(recipient, subject, content, "HAPPY");
                logger.info("Happy mail sent successfully to: {}", recipient);
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to send happy mail to: {} - Error: {}", recipient, e.getMessage());
                throw new RuntimeException("Failed to send happy mail to " + recipient, e);
            }
        }

        logger.info("All happy mails sent successfully to {} recipients for entity {}", 
                   successCount, currentEntityId);

        // Return processed entity (unchanged - workflow handles state transition)
        return entityWithMetadata;
    }

    /**
     * Generates happy mail content
     */
    private String generateHappyMailContent() {
        return """
            Dear Friend,
            
            We hope this message brings a smile to your face! 😊
            
            Here are some happy thoughts to brighten your day:
            - Every day is a new opportunity for joy
            - You are appreciated and valued
            - Good things are coming your way
            
            Have a wonderful day!
            
            Best regards,
            The Happy Mail Team
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
