package com.java_template.application.criterion;

import com.java_template.application.entity.email_notification_entity.version_1.EmailNotificationEntity;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * EmailSendingReadyCriterion - Check if email notification is ready to be sent
 * Transition: send_notification (scheduled → sending)
 */
@Component
public class EmailSendingReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Email validation pattern (RFC 5322 compliant)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public EmailSendingReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailSendingReady criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailNotificationEntity.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Check if email notification is ready to be sent including timing, recipient, and attachment availability
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailNotificationEntity> context) {
        EmailNotificationEntity entity = context.entityWithMetadata().entity();

        // Check if entity is null
        if (entity == null) {
            logger.warn("EmailNotificationEntity is null");
            return EvaluationOutcome.fail("Email notification entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if scheduled time is present
        if (entity.getScheduledTime() == null) {
            logger.warn("Scheduled time is null for notification: {}", entity.getNotificationId());
            return EvaluationOutcome.fail("Scheduled time is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if it's time to send
        if (entity.getScheduledTime().isAfter(LocalDateTime.now())) {
            logger.debug("Email notification {} not ready - scheduled for: {}", 
                    entity.getNotificationId(), entity.getScheduledTime());
            return EvaluationOutcome.fail("Email is not yet scheduled to be sent", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if recipient email is present and not empty
        if (entity.getRecipientEmail() == null || entity.getRecipientEmail().trim().isEmpty()) {
            logger.warn("Recipient email is null or empty for notification: {}", entity.getNotificationId());
            return EvaluationOutcome.fail("Recipient email is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate email format
        if (!EMAIL_PATTERN.matcher(entity.getRecipientEmail()).matches()) {
            logger.warn("Invalid email format for notification {}: {}", 
                    entity.getNotificationId(), entity.getRecipientEmail());
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if attachment file exists (simplified check - in real implementation would check file system)
        if (entity.getAttachmentPath() != null && !entity.getAttachmentPath().trim().isEmpty()) {
            // Simplified check - assume file exists if path is provided
            logger.debug("Attachment path provided for notification: {}", entity.getAttachmentPath());
        }

        // Check if we're within business hours for email sending (6 AM to 10 PM)
        LocalDateTime currentTime = LocalDateTime.now();
        int currentHour = currentTime.getHour();
        if (currentHour < 6 || currentHour > 22) {
            logger.debug("Email sending not ready - outside business hours: {}:00", currentHour);
            return EvaluationOutcome.fail("Email sending is restricted to business hours (6 AM - 10 PM)", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Email notification {} ready to be sent", entity.getNotificationId());
        return EvaluationOutcome.success();
    }
}
