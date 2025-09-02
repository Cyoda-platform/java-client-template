```java
package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

@Component
public class MailSendGloomyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MailSendGloomyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public MailSendGloomyMailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing gloomy mail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
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

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.isValid();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail entity = context.entity();

        // Validate that this is a gloomy mail
        if (entity.getIsHappy() == null || entity.getIsHappy()) {
            logger.error("Mail is not marked as gloomy");
            throw new RuntimeException("Mail is not marked as gloomy");
        }

        // Validate mail list
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.error("No recipients specified");
            throw new RuntimeException("No recipients specified");
        }

        // Process gloomy mail sending
        int successCount = 0;
        int failureCount = 0;

        String subject = "💙 Thoughtful Message - We're Here for You";
        String content = generateGloomyContent();

        for (String recipient : entity.getMailList()) {
            if (isValidEmail(recipient)) {
                try {
                    // Simulate email sending (in real implementation, this would call an email service)
                    boolean sendResult = sendEmail(recipient, subject, content);
                    if (sendResult) {
                        successCount++;
                        logger.debug("Successfully sent gloomy mail to: {}", recipient);
                    } else {
                        failureCount++;
                        logger.error("Failed to send gloomy mail to: {}", recipient);
                    }
                } catch (Exception e) {
                    failureCount++;
                    logger.error("Exception while sending gloomy mail to {}: {}", recipient, e.getMessage());
                }
            } else {
                failureCount++;
                logger.error("Invalid email address: {}", recipient);
            }
        }

        // Determine overall result
        if (successCount > 0 && failureCount == 0) {
            logger.info("Gloomy mail sent successfully to all {} recipients", successCount);
        } else if (successCount > 0) {
            logger.warn("Gloomy mail partially sent: {} success, {} failed", successCount, failureCount);
        } else {
            logger.error("Failed to send gloomy mail to any recipient");
            throw new RuntimeException("No recipients received the gloomy mail");
        }

        return entity;
    }

    private String generateGloomyContent() {
        return "Dear Friend,\n\n" +
               "We understand that not every day is bright, and that's okay.\n\n" +
               "Remember that difficult times don't last, but resilient people do. You're not alone.\n\n" +
               "Take care of yourself,\n" +
               "The Thoughtful Mail Team";
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean sendEmail(String recipient, String subject, String content) {
        // Simulate email sending - in real implementation this would integrate with an email service
        // For now, we'll simulate success for valid emails
        logger.info("Sending email to: {} with subject: {}", recipient, subject);
        return true; // Simulate successful sending
    }
}
```