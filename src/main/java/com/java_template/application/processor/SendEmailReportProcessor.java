package com.java_template.application.processor;

import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SendEmailReportProcessor
 * Send formatted email report to all subscribers
 */
@Component
public class SendEmailReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendEmailReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailNotification sending for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailNotification.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailNotification> entityWithMetadata) {
        EmailNotification entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     */
    private EntityWithMetadata<EmailNotification> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailNotification> context) {

        EntityWithMetadata<EmailNotification> entityWithMetadata = context.entityResponse();
        EmailNotification entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing EmailNotification sending: {} in state: {}", entity.getNotificationId(), currentState);

        try {
            // Validate email content is ready
            if (entity.getEmailSubject() == null || entity.getEmailSubject().trim().isEmpty()) {
                throw new RuntimeException("Email subject is not set");
            }
            
            if (entity.getEmailBody() == null || entity.getEmailBody().trim().isEmpty()) {
                throw new RuntimeException("Email body is not set");
            }
            
            if (entity.getSubscriberEmails() == null || entity.getSubscriberEmails().isEmpty()) {
                throw new RuntimeException("No subscriber emails provided");
            }
            
            // Send emails to all subscribers
            sendEmailsToSubscribers(entity);
            
            // Update the email notification entity
            entity.setSentAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            
            logger.info("EmailNotification {} sent successfully to {} subscribers", 
                entity.getNotificationId(), entity.getSubscriberEmails().size());
            
        } catch (Exception e) {
            logger.error("Failed to send email for EmailNotification: {}", entity.getNotificationId(), e);
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Send emails to all subscribers
     * In a real implementation, this would integrate with an email service like SendGrid, AWS SES, etc.
     */
    private void sendEmailsToSubscribers(EmailNotification entity) {
        logger.info("Sending email report to {} subscribers", entity.getSubscriberEmails().size());
        
        for (String email : entity.getSubscriberEmails()) {
            try {
                // Simulate email sending
                sendEmail(email, entity.getEmailSubject(), entity.getEmailBody());
                logger.debug("Email sent successfully to: {}", email);
                
                // Add a small delay to simulate real email sending
                Thread.sleep(100);
                
            } catch (Exception e) {
                logger.error("Failed to send email to: {}", email, e);
                throw new RuntimeException("Failed to send email to " + email + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Send individual email
     * This is a mock implementation - in production, integrate with actual email service
     */
    private void sendEmail(String toEmail, String subject, String body) throws InterruptedException {
        // Mock email sending implementation
        logger.info("MOCK EMAIL SEND:");
        logger.info("To: {}", toEmail);
        logger.info("Subject: {}", subject);
        logger.info("Body length: {} characters", body.length());
        logger.info("Body preview: {}", body.length() > 100 ? body.substring(0, 100) + "..." : body);
        
        // Simulate network delay
        Thread.sleep(50);
        
        // In a real implementation, you would:
        // 1. Use an email service like JavaMail, SendGrid, AWS SES, etc.
        // 2. Handle authentication and configuration
        // 3. Format the email properly (HTML/text)
        // 4. Handle delivery failures and retries
        // 5. Track delivery status
        
        // Example with JavaMail (commented out):
        /*
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("your-email@gmail.com", "your-password");
            }
        });
        
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("noreply@cyoda.com"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject);
        message.setText(body);
        
        Transport.send(message);
        */
    }
}
