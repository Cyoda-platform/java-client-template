package com.java_template.application.processor;

import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class EmailSendingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailSendingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public EmailSendingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailNotification sending for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailNotification.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailNotification entity) {
        return entity != null && entity.isValid();
    }

    private EmailNotification processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailNotification> context) {
        EmailNotification entity = context.entity();

        logger.info("Sending email to: {}", entity.getRecipientEmail());

        try {
            // Configure email client with SMTP settings (simulated)
            configureEmailClient();

            // Create and send email message
            boolean sendSuccess = sendEmailMessage(entity);

            if (sendSuccess) {
                entity.setActualSendTime(LocalDateTime.now());
                entity.setDeliveryStatus("SENT");
                entity.setErrorMessage(null); // Clear any previous error
                logger.info("Email sent successfully to: {}", entity.getRecipientEmail());
            } else {
                entity.setDeliveryStatus("FAILED");
                entity.setErrorMessage("SMTP send failed");
                logger.error("Failed to send email to: {}", entity.getRecipientEmail());
            }

        } catch (Exception e) {
            entity.setDeliveryStatus("FAILED");
            entity.setErrorMessage("Send error: " + e.getMessage());
            logger.error("Exception while sending email to {}: {}", entity.getRecipientEmail(), e.getMessage());
        }

        return entity;
    }

    private void configureEmailClient() {
        // Simulate SMTP configuration
        logger.info("Configuring SMTP client settings");
        // In a real implementation, this would configure:
        // - SMTP server host and port
        // - Authentication credentials
        // - SSL/TLS settings
        // - Connection timeouts
    }

    private boolean sendEmailMessage(EmailNotification email) {
        try {
            logger.info("Creating email message for: {}", email.getRecipientEmail());
            
            // Simulate email message creation
            String recipient = email.getRecipientEmail();
            String subject = email.getSubject();
            String body = formatEmailBody(email.getBodyContent());
            String attachment = email.getAttachmentPath();

            // Simulate SMTP sending
            logger.info("Sending email via SMTP:");
            logger.info("  To: {}", recipient);
            logger.info("  Subject: {}", subject);
            logger.info("  Attachment: {}", attachment);

            // Simulate send operation
            Thread.sleep(100); // Simulate network delay

            // Simulate success (in real implementation, this would be actual SMTP result)
            boolean success = simulateSmtpSend(email);
            
            if (success) {
                logger.info("SMTP send successful for: {}", recipient);
            } else {
                logger.warn("SMTP send failed for: {}", recipient);
            }

            return success;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Email sending interrupted");
            return false;
        } catch (Exception e) {
            logger.error("Error creating/sending email message: {}", e.getMessage());
            return false;
        }
    }

    private String formatEmailBody(String bodyContent) {
        // Format email body as HTML
        StringBuilder htmlBody = new StringBuilder();
        htmlBody.append("<html><body>");
        htmlBody.append("<div style='font-family: Arial, sans-serif; font-size: 14px;'>");
        
        // Convert line breaks to HTML
        String formattedContent = bodyContent.replace("\n", "<br>");
        htmlBody.append(formattedContent);
        
        htmlBody.append("</div>");
        htmlBody.append("</body></html>");
        
        return htmlBody.toString();
    }

    private boolean simulateSmtpSend(EmailNotification email) {
        // Simulate SMTP response
        // In a real implementation, this would be the actual SMTP send result
        
        // Simulate occasional failures for testing
        if (email.getRecipientEmail().contains("invalid")) {
            return false; // Simulate failure for invalid emails
        }
        
        // Simulate success for valid emails
        return true;
    }
}
