package com.java_template.application.criterion;

import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
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
import java.time.temporal.ChronoUnit;

@Component
public class EmailRetryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailRetryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking email retry criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailNotification.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailNotification> context) {
        EmailNotification entity = context.entity();
        logger.info("Checking retry eligibility for email to: {}", entity.getRecipientEmail());

        // Check retry eligibility
        if (!isRetryEligible(entity)) {
            return EvaluationOutcome.fail("Email is not eligible for retry", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check retry conditions
        if (!areRetryConditionsMet(entity)) {
            return EvaluationOutcome.fail("Retry conditions are not met", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Email is eligible for retry
        logger.info("Email to {} is eligible for retry", entity.getRecipientEmail());
        return EvaluationOutcome.success();
    }

    private boolean isRetryEligible(EmailNotification email) {
        // Check retry count is within limits
        int retryCount = email.getRetryCount() != null ? email.getRetryCount() : 0;
        int maxRetries = email.getMaxRetries() != null ? email.getMaxRetries() : 3;
        
        if (retryCount >= maxRetries) {
            logger.info("Email to {} has exceeded max retries ({}/{})", 
                       email.getRecipientEmail(), retryCount, maxRetries);
            return false;
        }

        // Check if failure was temporary (4xx SMTP code)
        if (email.getErrorMessage() != null) {
            String errorMessage = email.getErrorMessage().toLowerCase();
            
            // Permanent failures should not be retried
            if (errorMessage.contains("5") && (errorMessage.contains("smtp") || errorMessage.contains("mail"))) {
                logger.info("Email to {} has permanent SMTP failure, not eligible for retry", 
                           email.getRecipientEmail());
                return false;
            }
            
            // Check for specific permanent failure messages
            if (errorMessage.contains("user unknown") || 
                errorMessage.contains("mailbox unavailable") ||
                errorMessage.contains("domain not found") ||
                errorMessage.contains("recipient rejected")) {
                logger.info("Email to {} has permanent delivery failure, not eligible for retry", 
                           email.getRecipientEmail());
                return false;
            }
        }

        // Check time since last attempt (minimum retry interval)
        if (email.getActualSendTime() != null) {
            long minutesSinceLastAttempt = ChronoUnit.MINUTES.between(email.getActualSendTime(), LocalDateTime.now());
            int minimumRetryInterval = calculateMinimumRetryInterval(retryCount);
            
            if (minutesSinceLastAttempt < minimumRetryInterval) {
                logger.info("Email to {} attempted too recently, need to wait {} more minutes", 
                           email.getRecipientEmail(), minimumRetryInterval - minutesSinceLastAttempt);
                return false;
            }
        }

        return true;
    }

    private boolean areRetryConditionsMet(EmailNotification email) {
        // Check if SMTP server is now available (simulated)
        if (!isSmtpServerAvailable()) {
            logger.info("SMTP server is not available for retry");
            return false;
        }

        // Check if attachment file still exists
        if (email.getAttachmentPath() != null && !email.getAttachmentPath().trim().isEmpty()) {
            if (!doesAttachmentExist(email.getAttachmentPath())) {
                logger.info("Attachment file no longer exists: {}", email.getAttachmentPath());
                return false;
            }
        }

        // Check if recipient email is still valid
        if (!isRecipientEmailValid(email.getRecipientEmail())) {
            logger.info("Recipient email is no longer valid: {}", email.getRecipientEmail());
            return false;
        }

        // Check if email content has not expired
        if (hasEmailContentExpired(email)) {
            logger.info("Email content has expired for: {}", email.getRecipientEmail());
            return false;
        }

        return true;
    }

    private int calculateMinimumRetryInterval(int retryCount) {
        // Exponential backoff: 2^retryCount minutes
        return (int) Math.pow(2, retryCount);
    }

    private boolean isSmtpServerAvailable() {
        // In a real implementation, this would check SMTP server connectivity
        // For simulation, assume server is available 90% of the time
        return System.currentTimeMillis() % 10 != 0;
    }

    private boolean doesAttachmentExist(String attachmentPath) {
        // In a real implementation, this would check if the file exists
        // For simulation, assume files exist unless path contains "missing"
        return !attachmentPath.contains("missing");
    }

    private boolean isRecipientEmailValid(String recipientEmail) {
        // Basic email validation
        return recipientEmail != null && 
               recipientEmail.contains("@") && 
               !recipientEmail.contains("invalid") &&
               !recipientEmail.contains("..");
    }

    private boolean hasEmailContentExpired(EmailNotification email) {
        // Check if email content is still relevant
        if (email.getScheduledSendTime() != null) {
            long hoursSinceScheduled = ChronoUnit.HOURS.between(email.getScheduledSendTime(), LocalDateTime.now());
            // Consider email expired if scheduled more than 24 hours ago
            return hoursSinceScheduled > 24;
        }
        return false;
    }
}
