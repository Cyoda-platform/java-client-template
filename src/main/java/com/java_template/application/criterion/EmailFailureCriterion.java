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

@Component
public class EmailFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking email failure criteria for request: {}", request.getId());
        
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
        logger.info("Checking failure criteria for email to: {}", entity.getRecipientEmail());

        // Check SMTP response codes in error message
        if (entity.getErrorMessage() != null) {
            String errorMessage = entity.getErrorMessage().toLowerCase();
            
            // Check for permanent failure indicators (5xx codes)
            if (errorMessage.contains("5") && (errorMessage.contains("smtp") || errorMessage.contains("mail"))) {
                logger.info("Email to {} has permanent SMTP failure", entity.getRecipientEmail());
                return EvaluationOutcome.success();
            }
            
            // Check for specific permanent failure messages
            if (errorMessage.contains("user unknown") || 
                errorMessage.contains("mailbox unavailable") ||
                errorMessage.contains("domain not found") ||
                errorMessage.contains("recipient rejected")) {
                logger.info("Email to {} has permanent delivery failure", entity.getRecipientEmail());
                return EvaluationOutcome.success();
            }
        }

        // Check email content issues
        if (hasEmailContentIssues(entity)) {
            logger.info("Email to {} has content issues", entity.getRecipientEmail());
            return EvaluationOutcome.success();
        }

        // Check server constraints
        if (hasServerConstraintIssues(entity)) {
            logger.info("Email to {} has server constraint issues", entity.getRecipientEmail());
            return EvaluationOutcome.success();
        }

        // Check for temporary failures (4xx codes)
        if (entity.getErrorMessage() != null) {
            String errorMessage = entity.getErrorMessage().toLowerCase();
            if (errorMessage.contains("4") && (errorMessage.contains("smtp") || errorMessage.contains("mail"))) {
                logger.info("Email to {} has temporary SMTP failure", entity.getRecipientEmail());
                return EvaluationOutcome.fail("Email failure is temporary (4xx code)", 
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // No permanent failure detected
        logger.info("No permanent failure detected for email to: {}", entity.getRecipientEmail());
        return EvaluationOutcome.fail("Email failure is not permanent", 
                                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    private boolean hasEmailContentIssues(EmailNotification email) {
        // Check if attachment file is missing or corrupted
        if (email.getAttachmentPath() != null && !email.getAttachmentPath().trim().isEmpty()) {
            // In a real implementation, this would check if the file exists and is readable
            // For simulation, assume some attachments are missing
            if (email.getAttachmentPath().contains("missing") || 
                email.getAttachmentPath().contains("corrupted")) {
                return true;
            }
        }

        // Check email size constraints (simulated)
        if (email.getBodyContent() != null && email.getBodyContent().length() > 100000) {
            logger.warn("Email body content is very large: {} characters", email.getBodyContent().length());
            return true;
        }

        // Check invalid recipient email format
        if (email.getRecipientEmail() != null) {
            if (email.getRecipientEmail().contains("invalid") || 
                !email.getRecipientEmail().contains("@") ||
                email.getRecipientEmail().contains("..")) {
                return true;
            }
        }

        return false;
    }

    private boolean hasServerConstraintIssues(EmailNotification email) {
        // Check for authentication failures
        if (email.getErrorMessage() != null) {
            String errorMessage = email.getErrorMessage().toLowerCase();
            if (errorMessage.contains("authentication failed") ||
                errorMessage.contains("login failed") ||
                errorMessage.contains("invalid credentials")) {
                return true;
            }
            
            // Check for rate limiting
            if (errorMessage.contains("rate limit") ||
                errorMessage.contains("too many") ||
                errorMessage.contains("quota exceeded")) {
                return true;
            }
            
            // Check for server unavailable
            if (errorMessage.contains("server unavailable") ||
                errorMessage.contains("connection refused") ||
                errorMessage.contains("timeout")) {
                // These are typically temporary, but if persistent could be permanent
                return false; // Treat as temporary for now
            }
        }

        return false;
    }
}
