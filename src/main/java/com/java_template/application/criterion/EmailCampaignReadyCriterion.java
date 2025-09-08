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

import java.time.LocalDateTime;

/**
 * EmailCampaignReadyCriterion - Validates campaign is ready to be sent
 * 
 * Purpose: Validate campaign is ready to be sent
 * Input: EmailCampaign entity
 * Output: Boolean (true if ready, false otherwise)
 * 
 * Use Cases:
 * - SCHEDULED → SENDING transition
 */
@Component
public class EmailCampaignReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailCampaignReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailCampaign ready criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, this::validateEmailCampaignReady)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for email campaign readiness
     */
    private EvaluationOutcome validateEmailCampaignReady(CriterionSerializer.CriterionEntityEvaluationContext<EmailCampaign> context) {
        EmailCampaign campaign = context.entityWithMetadata().entity();

        // Check if campaign is null (structural validation)
        if (campaign == null) {
            logger.warn("EmailCampaign is null");
            return EvaluationOutcome.fail("EmailCampaign entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!campaign.isValid()) {
            logger.warn("EmailCampaign {} is not valid", campaign.getCampaignId());
            return EvaluationOutcome.fail("EmailCampaign entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if scheduled date is in the future
        if (campaign.getScheduledDate() != null && campaign.getScheduledDate().isAfter(LocalDateTime.now())) {
            logger.debug("EmailCampaign {} scheduled date is in the future", campaign.getCampaignId());
            return EvaluationOutcome.fail("Scheduled date is in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if cat fact ID is valid
        if (campaign.getCatFactId() == null || campaign.getCatFactId().trim().isEmpty()) {
            logger.warn("EmailCampaign {} has invalid cat fact ID", campaign.getCampaignId());
            return EvaluationOutcome.fail("Invalid cat fact ID", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if there are subscribers
        if (campaign.getTotalSubscribers() <= 0) {
            logger.warn("EmailCampaign {} has no subscribers", campaign.getCampaignId());
            return EvaluationOutcome.fail("No subscribers for campaign", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if email subject is valid
        if (campaign.getEmailSubject() == null || campaign.getEmailSubject().trim().isEmpty()) {
            logger.warn("EmailCampaign {} has empty email subject", campaign.getCampaignId());
            return EvaluationOutcome.fail("Empty email subject", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if email template is valid
        if (campaign.getEmailTemplate() == null || campaign.getEmailTemplate().trim().isEmpty()) {
            logger.warn("EmailCampaign {} has empty email template", campaign.getCampaignId());
            return EvaluationOutcome.fail("Empty email template", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("EmailCampaign {} is ready to be sent", campaign.getCampaignId());
        return EvaluationOutcome.success();
    }
}
