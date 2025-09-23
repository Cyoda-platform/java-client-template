package com.java_template.application.criterion;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
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

/**
 * EmailCampaignValidationCriterion - Validates email campaign readiness and configuration
 * 
 * This criterion validates email campaign setup, configuration,
 * and readiness for execution.
 */
@Component
public class EmailCampaignValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailCampaignValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailCampaign validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, this::validateEmailCampaign)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the email campaign entity
     */
    private EvaluationOutcome validateEmailCampaign(CriterionSerializer.CriterionEntityEvaluationContext<EmailCampaign> context) {
        EmailCampaign entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("EmailCampaign entity is null");
            return EvaluationOutcome.fail("EmailCampaign entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate required fields
        EvaluationOutcome requiredFieldsResult = validateRequiredFields(entity);
        if (!requiredFieldsResult.isSuccess()) {
            return requiredFieldsResult;
        }

        // Validate campaign configuration
        EvaluationOutcome configResult = validateCampaignConfiguration(entity);
        if (!configResult.isSuccess()) {
            return configResult;
        }

        // Validate business rules
        EvaluationOutcome businessRulesResult = validateBusinessRules(entity);
        if (!businessRulesResult.isSuccess()) {
            return businessRulesResult;
        }

        // Validate metrics consistency if present
        if (entity.getMetrics() != null) {
            EvaluationOutcome metricsResult = validateMetrics(entity);
            if (!metricsResult.isSuccess()) {
                return metricsResult;
            }
        }

        logger.debug("EmailCampaign {} passed all validation criteria", entity.getCampaignId());
        return EvaluationOutcome.success();
    }

    /**
     * Validates required fields are present and not empty
     */
    private EvaluationOutcome validateRequiredFields(EmailCampaign entity) {
        if (entity.getCampaignId() == null || entity.getCampaignId().trim().isEmpty()) {
            logger.warn("Campaign ID is missing or empty");
            return EvaluationOutcome.fail("Campaign ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getCatFactId() == null || entity.getCatFactId().trim().isEmpty()) {
            logger.warn("Cat fact ID is missing for campaign: {}", entity.getCampaignId());
            return EvaluationOutcome.fail("Cat fact ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getRecipientCount() == null) {
            logger.warn("Recipient count is missing for campaign: {}", entity.getCampaignId());
            return EvaluationOutcome.fail("Recipient count is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getSuccessCount() == null) {
            logger.warn("Success count is missing for campaign: {}", entity.getCampaignId());
            return EvaluationOutcome.fail("Success count is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getFailureCount() == null) {
            logger.warn("Failure count is missing for campaign: {}", entity.getCampaignId());
            return EvaluationOutcome.fail("Failure count is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates campaign configuration and setup
     */
    private EvaluationOutcome validateCampaignConfiguration(EmailCampaign entity) {
        // Validate email subject if present
        if (entity.getEmailSubject() != null) {
            String subject = entity.getEmailSubject().trim();
            if (subject.isEmpty()) {
                logger.warn("Email subject is empty for campaign: {}", entity.getCampaignId());
                return EvaluationOutcome.fail("Email subject cannot be empty",
                                            StandardEvalReasonCategories.STRUCTURAL_FAILURE);
            }
            
            if (subject.length() > 200) {
                logger.warn("Email subject too long for campaign {}: {} characters", 
                           entity.getCampaignId(), subject.length());
                return EvaluationOutcome.fail("Email subject is too long (maximum 200 characters)",
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Validate campaign name if present
        if (entity.getCampaignName() != null) {
            String name = entity.getCampaignName().trim();
            if (name.isEmpty()) {
                logger.warn("Campaign name is empty for campaign: {}", entity.getCampaignId());
                return EvaluationOutcome.fail("Campaign name cannot be empty",
                                            StandardEvalReasonCategories.STRUCTURAL_FAILURE);
            }
            
            if (name.length() > 100) {
                logger.warn("Campaign name too long for campaign {}: {} characters", 
                           entity.getCampaignId(), name.length());
                return EvaluationOutcome.fail("Campaign name is too long (maximum 100 characters)", 
                                            StandardEvalReasonCategories.CONFIGURATION_FAILURE);
            }
        }

        // Validate email template if present
        if (entity.getEmailTemplate() != null && entity.getEmailTemplate().trim().isEmpty()) {
            logger.warn("Email template is empty for campaign: {}", entity.getCampaignId());
            return EvaluationOutcome.fail("Email template cannot be empty", 
                                        StandardEvalReasonCategories.CONFIGURATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates business rules for email campaigns
     */
    private EvaluationOutcome validateBusinessRules(EmailCampaign entity) {
        // Validate count consistency
        int recipientCount = entity.getRecipientCount();
        int successCount = entity.getSuccessCount();
        int failureCount = entity.getFailureCount();

        if (recipientCount < 0) {
            logger.warn("Negative recipient count for campaign {}: {}", 
                       entity.getCampaignId(), recipientCount);
            return EvaluationOutcome.fail("Recipient count cannot be negative", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (successCount < 0) {
            logger.warn("Negative success count for campaign {}: {}", 
                       entity.getCampaignId(), successCount);
            return EvaluationOutcome.fail("Success count cannot be negative", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (failureCount < 0) {
            logger.warn("Negative failure count for campaign {}: {}", 
                       entity.getCampaignId(), failureCount);
            return EvaluationOutcome.fail("Failure count cannot be negative", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check that success + failure doesn't exceed recipients
        if (successCount + failureCount > recipientCount) {
            logger.warn("Success + failure count exceeds recipients for campaign {}: {} + {} > {}", 
                       entity.getCampaignId(), successCount, failureCount, recipientCount);
            return EvaluationOutcome.fail("Success and failure counts cannot exceed recipient count", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate status consistency
        if (entity.getStatus() != null) {
            EvaluationOutcome statusResult = validateStatusConsistency(entity);
            if (!statusResult.isSuccess()) {
                return statusResult;
            }
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates status consistency with other fields
     */
    private EvaluationOutcome validateStatusConsistency(EmailCampaign entity) {
        EmailCampaign.CampaignStatus status = entity.getStatus();
        
        // If campaign is completed, it should have a sent date
        if (status == EmailCampaign.CampaignStatus.COMPLETED && entity.getSentDate() == null) {
            logger.warn("Completed campaign {} missing sent date", entity.getCampaignId());
            return EvaluationOutcome.fail("Completed campaigns must have a sent date", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // If campaign is sending or completed, it should have some recipients
        if ((status == EmailCampaign.CampaignStatus.SENDING || status == EmailCampaign.CampaignStatus.COMPLETED) 
            && entity.getRecipientCount() == 0) {
            logger.warn("Campaign {} in {} status but has no recipients", 
                       entity.getCampaignId(), status);
            return EvaluationOutcome.fail("Sending or completed campaigns must have recipients", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates metrics consistency and calculations
     */
    private EvaluationOutcome validateMetrics(EmailCampaign entity) {
        EmailCampaign.CampaignMetrics metrics = entity.getMetrics();

        // Validate rate ranges (should be between 0 and 1)
        if (metrics.getDeliveryRate() != null && 
            (metrics.getDeliveryRate() < 0.0 || metrics.getDeliveryRate() > 1.0)) {
            logger.warn("Invalid delivery rate for campaign {}: {}", 
                       entity.getCampaignId(), metrics.getDeliveryRate());
            return EvaluationOutcome.fail("Delivery rate must be between 0.0 and 1.0", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (metrics.getOpenRate() != null && 
            (metrics.getOpenRate() < 0.0 || metrics.getOpenRate() > 1.0)) {
            logger.warn("Invalid open rate for campaign {}: {}", 
                       entity.getCampaignId(), metrics.getOpenRate());
            return EvaluationOutcome.fail("Open rate must be between 0.0 and 1.0", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (metrics.getClickRate() != null && 
            (metrics.getClickRate() < 0.0 || metrics.getClickRate() > 1.0)) {
            logger.warn("Invalid click rate for campaign {}: {}", 
                       entity.getCampaignId(), metrics.getClickRate());
            return EvaluationOutcome.fail("Click rate must be between 0.0 and 1.0", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (metrics.getBounceRate() != null && 
            (metrics.getBounceRate() < 0.0 || metrics.getBounceRate() > 1.0)) {
            logger.warn("Invalid bounce rate for campaign {}: {}", 
                       entity.getCampaignId(), metrics.getBounceRate());
            return EvaluationOutcome.fail("Bounce rate must be between 0.0 and 1.0", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate count consistency
        if (metrics.getTotalOpens() != null && metrics.getTotalOpens() < 0) {
            logger.warn("Negative total opens for campaign {}: {}", 
                       entity.getCampaignId(), metrics.getTotalOpens());
            return EvaluationOutcome.fail("Total opens cannot be negative", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (metrics.getTotalClicks() != null && metrics.getTotalClicks() < 0) {
            logger.warn("Negative total clicks for campaign {}: {}", 
                       entity.getCampaignId(), metrics.getTotalClicks());
            return EvaluationOutcome.fail("Total clicks cannot be negative", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Clicks should not exceed opens
        if (metrics.getTotalClicks() != null && metrics.getTotalOpens() != null &&
            metrics.getTotalClicks() > metrics.getTotalOpens()) {
            logger.warn("Total clicks exceeds total opens for campaign {}: {} > {}", 
                       entity.getCampaignId(), metrics.getTotalClicks(), metrics.getTotalOpens());
            return EvaluationOutcome.fail("Total clicks cannot exceed total opens", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
