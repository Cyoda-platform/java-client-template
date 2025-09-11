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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MailSendGloomyMailProcessor - Processes and sends gloomy mail to recipients
 * 
 * This processor handles the business logic for sending gloomy mail to all recipients
 * in the mail list. It validates that the mail is marked as gloomy and sends
 * appropriate thoughtful content to each recipient.
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
     * This method checks both the entity and metadata are valid
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Mail> entityWithMetadata) {
        Mail entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method for sending gloomy mail
     * 
     * CRITICAL LIMITATIONS:
     * - ✅ ALLOWED: Read current entity data
     * - ❌ FORBIDDEN: Update current entity state/transitions
     */
    private EntityWithMetadata<Mail> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Mail> context) {

        EntityWithMetadata<Mail> entityWithMetadata = context.entityResponse();
        Mail entity = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing gloomy mail entity: {} in state: {}", currentEntityId, currentState);

        // Validate input - mail must be marked as gloomy (isHappy = false)
        if (!Boolean.FALSE.equals(entity.getIsHappy())) {
            logger.error("Mail is not marked as gloomy - cannot process with gloomy mail processor");
            throw new IllegalArgumentException("Mail is not marked as gloomy");
        }

        // Validate mail list is not empty
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.error("Mail list cannot be empty");
            throw new IllegalArgumentException("Mail list cannot be empty");
        }

        // Prepare gloomy mail content
        String subject = "Thoughtful Message - A Moment of Reflection";
        String content = "Hello. Sometimes life brings challenges and difficult moments. Remember that it's okay to feel down sometimes, and brighter days are ahead. Take care of yourself.";

        // Send mail to each recipient
        for (String email : entity.getMailList()) {
            try {
                sendMail(email, subject, content);
                logger.info("Gloomy mail sent successfully to: {}", email);
            } catch (Exception e) {
                logger.error("Failed to send gloomy mail to: {}", email, e);
                throw new RuntimeException("Failed to send mail to " + email, e);
            }
        }

        logger.info("All gloomy mails sent successfully for entity: {}", currentEntityId);

        // CRITICAL: Return EntityWithMetadata unchanged (state transition handled by workflow)
        return entityWithMetadata;
    }

    /**
     * Simulates sending an email (in a real implementation, this would integrate with an email service)
     */
    private void sendMail(String email, String subject, String content) {
        // Simulate email sending with a small delay
        try {
            Thread.sleep(10); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email sending interrupted", e);
        }
        
        // Log the email sending (in real implementation, this would call an actual email service)
        logger.info("SIMULATED EMAIL SEND - To: {}, Subject: {}, Content: {}", email, subject, content);
        
        // Simulate potential failure (uncomment to test error handling)
        // if (email.contains("fail")) {
        //     throw new RuntimeException("Simulated email sending failure");
        // }
    }
}
