package com.java_template.application.criterion;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Criterion to determine if a campaign should be marked as failed.
 * Handles the fail transition (sending → failed).
 * 
 * Validation Logic:
 * - Checks if total emails = 0 (no emails to send)
 * - Checks if failure rate >= 50% (too many failed deliveries)
 * - Checks if sending duration > 2 hours (took too long)
 * - Checks for system errors during sending
 */
@Component
public class EmailCampaignFailureCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignFailureCriterion.class);
    private static final double MAX_FAILURE_RATE = 0.5; // 50%
    private static final long MAX_SEND_DURATION_HOURS = 2;
    
    private final CriterionSerializer serializer;

    public EmailCampaignFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("EmailCampaignFailureCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking email campaign failure criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, ctx -> this.evaluateCampaignFailure(ctx.entity()))
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignFailureCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether a campaign should be marked as failed.
     * 
     * @param campaign The email campaign to evaluate
     * @return EvaluationOutcome indicating whether failure criteria are met
     */
    private EvaluationOutcome evaluateCampaignFailure(EmailCampaign campaign) {
        if (campaign == null) {
            return EvaluationOutcome.fail("Email campaign is null");
        }

        // Check if total emails = 0
        if (campaign.getTotalSubscribers() == null || campaign.getTotalSubscribers() == 0) {
            logger.info("Campaign {} meets failure criteria: no emails to send", campaign.getCampaignName());
            return EvaluationOutcome.success(); // Criteria met for failure
        }

        // Initialize counters if null
        int successfulDeliveries = campaign.getSuccessfulDeliveries() != null ? campaign.getSuccessfulDeliveries() : 0;
        int failedDeliveries = campaign.getFailedDeliveries() != null ? campaign.getFailedDeliveries() : 0;
        int totalDeliveries = successfulDeliveries + failedDeliveries;

        // Check failure rate if we have attempted deliveries
        if (totalDeliveries > 0) {
            double failureRate = (double) failedDeliveries / totalDeliveries;
            
            if (failureRate >= MAX_FAILURE_RATE) {
                logger.info("Campaign {} meets failure criteria: high failure rate ({:.2f}%)", 
                           campaign.getCampaignName(), failureRate * 100);
                return EvaluationOutcome.success(); // Criteria met for failure
            }
        }

        // Check sending duration
        if (campaign.getScheduledDate() != null && campaign.getSentDate() != null) {
            Duration sendDuration = Duration.between(campaign.getScheduledDate(), campaign.getSentDate());
            
            if (sendDuration.toHours() > MAX_SEND_DURATION_HOURS) {
                logger.info("Campaign {} meets failure criteria: sending duration exceeded {} hours ({}h)", 
                           campaign.getCampaignName(), MAX_SEND_DURATION_HOURS, sendDuration.toHours());
                return EvaluationOutcome.success(); // Criteria met for failure
            }
        }

        // Check for system errors (simplified - in reality would check error logs/flags)
        if (hasSystemError(campaign)) {
            logger.info("Campaign {} meets failure criteria: system error detected", campaign.getCampaignName());
            return EvaluationOutcome.success(); // Criteria met for failure
        }

        // No failure criteria met
        logger.debug("Campaign {} does not meet failure criteria", campaign.getCampaignName());
        return EvaluationOutcome.fail("No failure criteria met");
    }

    /**
     * Checks if the campaign has system errors.
     * In a real implementation, this would check error logs, system status, etc.
     */
    private boolean hasSystemError(EmailCampaign campaign) {
        // Simplified check - in reality, this would involve checking:
        // - Error logs
        // - System status flags
        // - External service availability
        // - Database connection issues
        // - Email service status
        
        // For now, we'll check if the campaign has been running for too long without progress
        if (campaign.getSentDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            Duration timeSinceSent = Duration.between(campaign.getSentDate(), now);
            
            // If campaign has been "sending" for more than 4 hours, consider it a system error
            if (timeSinceSent.toHours() > 4) {
                return true;
            }
        }
        
        // Check if we have a very low delivery rate which might indicate system issues
        int totalSubscribers = campaign.getTotalSubscribers() != null ? campaign.getTotalSubscribers() : 0;
        int successfulDeliveries = campaign.getSuccessfulDeliveries() != null ? campaign.getSuccessfulDeliveries() : 0;
        int failedDeliveries = campaign.getFailedDeliveries() != null ? campaign.getFailedDeliveries() : 0;
        int totalDeliveries = successfulDeliveries + failedDeliveries;
        
        // If we've attempted to send to less than 10% of subscribers, might be a system issue
        if (totalSubscribers > 0 && totalDeliveries < (totalSubscribers * 0.1)) {
            return true;
        }
        
        return false;
    }
}
