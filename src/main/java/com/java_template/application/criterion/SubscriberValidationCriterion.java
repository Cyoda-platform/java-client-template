package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
 * SubscriberValidationCriterion - Validates subscriber data quality and business rules
 * 
 * This criterion validates subscriber information including email format,
 * required fields, and business rule compliance.
 */
@Component
public class SubscriberValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    public SubscriberValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Subscriber validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::validateSubscriber)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the subscriber entity
     */
    private EvaluationOutcome validateSubscriber(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
        Subscriber entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("Subscriber entity is null");
            return EvaluationOutcome.fail("Subscriber entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate required fields
        EvaluationOutcome requiredFieldsResult = validateRequiredFields(entity);
        if (!requiredFieldsResult.isSuccess()) {
            return requiredFieldsResult;
        }

        // Validate email format
        EvaluationOutcome emailResult = validateEmailFormat(entity);
        if (!emailResult.isSuccess()) {
            return emailResult;
        }

        // Validate business rules
        EvaluationOutcome businessRulesResult = validateBusinessRules(entity);
        if (!businessRulesResult.isSuccess()) {
            return businessRulesResult;
        }

        // Validate data quality
        EvaluationOutcome dataQualityResult = validateDataQuality(entity);
        if (!dataQualityResult.isSuccess()) {
            return dataQualityResult;
        }

        logger.debug("Subscriber {} passed all validation criteria", entity.getSubscriberId());
        return EvaluationOutcome.success();
    }

    /**
     * Validates required fields are present and not empty
     */
    private EvaluationOutcome validateRequiredFields(Subscriber entity) {
        if (entity.getSubscriberId() == null || entity.getSubscriberId().trim().isEmpty()) {
            logger.warn("Subscriber ID is missing or empty");
            return EvaluationOutcome.fail("Subscriber ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getEmail() == null || entity.getEmail().trim().isEmpty()) {
            logger.warn("Email is missing for subscriber: {}", entity.getSubscriberId());
            return EvaluationOutcome.fail("Email address is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            logger.warn("Name is missing for subscriber: {}", entity.getSubscriberId());
            return EvaluationOutcome.fail("Subscriber name is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getSubscriptionDate() == null) {
            logger.warn("Subscription date is missing for subscriber: {}", entity.getSubscriberId());
            return EvaluationOutcome.fail("Subscription date is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getIsActive() == null) {
            logger.warn("Active status is missing for subscriber: {}", entity.getSubscriberId());
            return EvaluationOutcome.fail("Active status is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates email format using regex pattern
     */
    private EvaluationOutcome validateEmailFormat(Subscriber entity) {
        String email = entity.getEmail();
        
        if (email != null && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            logger.warn("Invalid email format for subscriber {}: {}", entity.getSubscriberId(), email);
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates business rules for subscribers
     */
    private EvaluationOutcome validateBusinessRules(Subscriber entity) {
        // Check for reasonable name length
        if (entity.getName() != null && entity.getName().length() > 100) {
            logger.warn("Name too long for subscriber {}: {} characters", 
                       entity.getSubscriberId(), entity.getName().length());
            return EvaluationOutcome.fail("Subscriber name is too long (max 100 characters)", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check for reasonable email length
        if (entity.getEmail() != null && entity.getEmail().length() > 254) {
            logger.warn("Email too long for subscriber {}: {} characters", 
                       entity.getSubscriberId(), entity.getEmail().length());
            return EvaluationOutcome.fail("Email address is too long (max 254 characters)", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate subscriber ID format (should be reasonable length and format)
        if (entity.getSubscriberId() != null && entity.getSubscriberId().length() > 50) {
            logger.warn("Subscriber ID too long: {}", entity.getSubscriberId());
            return EvaluationOutcome.fail("Subscriber ID is too long (max 50 characters)", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates data quality and consistency
     */
    private EvaluationOutcome validateDataQuality(Subscriber entity) {
        // Check for suspicious email patterns
        if (entity.getEmail() != null) {
            String email = entity.getEmail().toLowerCase();
            
            // Check for obviously fake emails
            if (email.contains("test@test") || email.contains("fake@fake") || 
                email.contains("example@example") || email.equals("test@test.com")) {
                logger.warn("Suspicious email detected for subscriber {}: {}", 
                           entity.getSubscriberId(), entity.getEmail());
                return EvaluationOutcome.fail("Email appears to be a test or fake address", 
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Check for reasonable name patterns
        if (entity.getName() != null) {
            String name = entity.getName().trim();
            
            // Check for obviously fake names
            if (name.toLowerCase().equals("test") || name.toLowerCase().equals("fake") ||
                name.toLowerCase().equals("test user") || name.toLowerCase().equals("john doe")) {
                logger.warn("Suspicious name detected for subscriber {}: {}", 
                           entity.getSubscriberId(), entity.getName());
                return EvaluationOutcome.fail("Name appears to be a test or placeholder value", 
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Validate preferences if present
        if (entity.getPreferences() != null) {
            EvaluationOutcome preferencesResult = validatePreferences(entity.getPreferences());
            if (!preferencesResult.isSuccess()) {
                return preferencesResult;
            }
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates subscriber preferences
     */
    private EvaluationOutcome validatePreferences(Subscriber.SubscriberPreferences preferences) {
        // Validate email format preference
        if (preferences.getEmailFormat() != null) {
            String format = preferences.getEmailFormat().toUpperCase();
            if (!format.equals("HTML") && !format.equals("TEXT")) {
                logger.warn("Invalid email format preference: {}", preferences.getEmailFormat());
                return EvaluationOutcome.fail("Email format must be HTML or TEXT", 
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Validate frequency preference
        if (preferences.getFrequency() != null) {
            String frequency = preferences.getFrequency().toUpperCase();
            if (!frequency.equals("WEEKLY") && !frequency.equals("DAILY") && !frequency.equals("MONTHLY")) {
                logger.warn("Invalid frequency preference: {}", preferences.getFrequency());
                return EvaluationOutcome.fail("Frequency must be WEEKLY, DAILY, or MONTHLY", 
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
