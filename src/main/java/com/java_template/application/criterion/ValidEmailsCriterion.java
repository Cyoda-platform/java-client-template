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

import java.util.regex.Pattern;

/**
 * ValidEmailsCriterion
 * Validate that all subscriber emails are valid
 */
@Component
public class ValidEmailsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    // Simple email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    public ValidEmailsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ValidEmails criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailNotification.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the entity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailNotification> context) {
        EmailNotification entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("EmailNotification is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("EmailNotification is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if subscriber emails are provided
        if (entity.getSubscriberEmails() == null || entity.getSubscriberEmails().isEmpty()) {
            logger.warn("Subscriber emails are null or empty");
            return EvaluationOutcome.fail("Subscriber emails are required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate each email address
        for (String email : entity.getSubscriberEmails()) {
            if (email == null || email.trim().isEmpty()) {
                logger.warn("Empty email address found in subscriber list");
                return EvaluationOutcome.fail("Empty email address found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            
            if (!isValidEmail(email.trim())) {
                logger.warn("Invalid email address: {}", email);
                return EvaluationOutcome.fail("Invalid email address: " + email, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        logger.debug("Email validation successful for {} emails", entity.getSubscriberEmails().size());
        return EvaluationOutcome.success();
    }

    /**
     * Validate email address format
     */
    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
