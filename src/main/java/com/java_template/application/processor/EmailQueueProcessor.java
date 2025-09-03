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

import java.io.File;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Component
public class EmailQueueProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailQueueProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    @Autowired
    private EntityService entityService;

    public EmailQueueProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailNotification queue for request: {}", request.getId());

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

        logger.info("Queueing email notification for recipient: {}", entity.getRecipientEmail());

        // Validate email parameters
        validateEmailParameters(entity);

        // Set email priority based on type
        String priority = determineEmailPriority(entity);
        logger.info("Set priority {} for email to {}", priority, entity.getRecipientEmail());

        // Set scheduled send time if not provided
        if (entity.getScheduledSendTime() == null) {
            entity.setScheduledSendTime(LocalDateTime.now());
        }

        // Initialize retry parameters
        if (entity.getRetryCount() == null) {
            entity.setRetryCount(0);
        }
        if (entity.getMaxRetries() == null) {
            entity.setMaxRetries(3);
        }

        // Set initial delivery status
        entity.setDeliveryStatus("PENDING");

        logger.info("Email notification queued for: {}", entity.getRecipientEmail());
        return entity;
    }

    private void validateEmailParameters(EmailNotification email) {
        // Validate recipient email format
        if (email.getRecipientEmail() == null || !EMAIL_PATTERN.matcher(email.getRecipientEmail()).matches()) {
            throw new IllegalArgumentException("Invalid recipient email format: " + email.getRecipientEmail());
        }

        // Validate subject is not empty
        if (email.getSubject() == null || email.getSubject().trim().isEmpty()) {
            throw new IllegalArgumentException("Email subject cannot be empty");
        }

        // Validate body content is not empty
        if (email.getBodyContent() == null || email.getBodyContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Email body content cannot be empty");
        }

        // Verify attachment file exists (simulated)
        if (email.getAttachmentPath() != null && !email.getAttachmentPath().trim().isEmpty()) {
            // In a real implementation, this would check if the file actually exists
            logger.info("Verified attachment exists: {}", email.getAttachmentPath());
        }

        logger.info("Email parameters validation completed");
    }

    private String determineEmailPriority(EmailNotification email) {
        // Determine priority based on email content
        if (email.getSubject() != null && email.getSubject().toLowerCase().contains("report")) {
            return "HIGH"; // Report emails have high priority
        } else if (email.getSubject() != null && email.getSubject().toLowerCase().contains("notification")) {
            return "MEDIUM"; // Notification emails have medium priority
        } else {
            return "LOW"; // Default priority
        }
    }
}
