package com.java_template.application.criterion;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Criterion to check email sending rate limits.
 * Used for email campaign sending to prevent overwhelming email services.
 * 
 * Validation Logic:
 * - Checks if emails sent in last hour < 1000 (hourly limit)
 * - Checks if emails sent in last day < 10000 (daily limit)
 * - Checks if campaigns sent in last hour < 5 (campaign frequency limit)
 * - Returns success if within rate limits
 */
@Component
public class EmailRateLimitCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(EmailRateLimitCriterion.class);
    private static final int HOURLY_EMAIL_LIMIT = 1000;
    private static final int DAILY_EMAIL_LIMIT = 10000;
    private static final int HOURLY_CAMPAIGN_LIMIT = 5;
    
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    public EmailRateLimitCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        logger.debug("EmailRateLimitCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking email rate limit criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, ctx -> this.evaluateEmailRateLimit(ctx.entity()))
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailRateLimitCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether email rate limits are within acceptable bounds.
     * 
     * @param campaign The email campaign to evaluate
     * @return EvaluationOutcome indicating whether rate limits are OK
     */
    private EvaluationOutcome evaluateEmailRateLimit(EmailCampaign campaign) {
        if (campaign == null) {
            return EvaluationOutcome.fail("Email campaign is null");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        LocalDateTime oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        try {
            // Check hourly email limit
            int emailsSentLastHour = getEmailsSentSince(oneHourAgo);
            int proposedEmails = campaign.getTotalSubscribers() != null ? campaign.getTotalSubscribers() : 0;
            
            if (emailsSentLastHour + proposedEmails > HOURLY_EMAIL_LIMIT) {
                return EvaluationOutcome.fail(String.format(
                    "Hourly email limit exceeded: %d sent + %d proposed > %d limit", 
                    emailsSentLastHour, proposedEmails, HOURLY_EMAIL_LIMIT));
            }

            // Check daily email limit
            int emailsSentLastDay = getEmailsSentSince(oneDayAgo);
            
            if (emailsSentLastDay + proposedEmails > DAILY_EMAIL_LIMIT) {
                return EvaluationOutcome.fail(String.format(
                    "Daily email limit exceeded: %d sent + %d proposed > %d limit", 
                    emailsSentLastDay, proposedEmails, DAILY_EMAIL_LIMIT));
            }

            // Check hourly campaign limit
            int campaignsSentLastHour = getCampaignsSentSince(oneHourAgo);
            
            if (campaignsSentLastHour >= HOURLY_CAMPAIGN_LIMIT) {
                return EvaluationOutcome.fail(String.format(
                    "Hourly campaign limit exceeded: %d campaigns sent > %d limit", 
                    campaignsSentLastHour, HOURLY_CAMPAIGN_LIMIT));
            }

            // All rate limits are within bounds
            logger.debug("Email rate limits OK: {}h emails: {}, {}h campaigns: {}", 
                        1, emailsSentLastHour, 1, campaignsSentLastHour);
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Failed to check email rate limits: {}", e.getMessage());
            return EvaluationOutcome.fail("Failed to verify email rate limits");
        }
    }

    /**
     * Gets the number of emails sent since the specified time.
     */
    private int getEmailsSentSince(LocalDateTime since) {
        try {
            // Get all campaigns sent since the specified time
            List<org.cyoda.cloud.api.event.common.DataPayload> recentCampaigns = 
                getRecentCampaigns(since);
            
            int totalEmails = 0;
            for (org.cyoda.cloud.api.event.common.DataPayload campaignData : recentCampaigns) {
                com.fasterxml.jackson.databind.JsonNode data = campaignData.getData();
                
                // Check if campaign was sent
                if (data.has("sentDate") && !data.get("sentDate").isNull()) {
                    // Add successful deliveries
                    if (data.has("successfulDeliveries")) {
                        totalEmails += data.get("successfulDeliveries").asInt(0);
                    }
                }
            }
            
            return totalEmails;
            
        } catch (Exception e) {
            logger.error("Failed to count emails sent since {}: {}", since, e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the number of campaigns sent since the specified time.
     */
    private int getCampaignsSentSince(LocalDateTime since) {
        try {
            List<org.cyoda.cloud.api.event.common.DataPayload> recentCampaigns = 
                getRecentCampaigns(since);
            
            int campaignCount = 0;
            for (org.cyoda.cloud.api.event.common.DataPayload campaignData : recentCampaigns) {
                com.fasterxml.jackson.databind.JsonNode data = campaignData.getData();
                
                // Check if campaign was sent
                if (data.has("sentDate") && !data.get("sentDate").isNull()) {
                    campaignCount++;
                }
            }
            
            return campaignCount;
            
        } catch (Exception e) {
            logger.error("Failed to count campaigns sent since {}: {}", since, e.getMessage());
            return 0;
        }
    }

    /**
     * Gets campaigns that were active since the specified time.
     */
    private List<org.cyoda.cloud.api.event.common.DataPayload> getRecentCampaigns(LocalDateTime since) {
        try {
            // In a real implementation, this would use a more sophisticated query
            // to filter campaigns by date range. For now, we get all campaigns
            // and filter in memory (not efficient for large datasets)
            
            Map<String, Object> condition = new HashMap<>();
            // We could add date range conditions here if the EntityService supports it
            
            List<org.cyoda.cloud.api.event.common.DataPayload> allCampaigns = 
                entityService.getItemsByCondition(
                    EmailCampaign.ENTITY_NAME, 
                    EmailCampaign.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            // Filter campaigns by date (simplified)
            return allCampaigns.stream()
                .filter(campaignData -> {
                    try {
                        com.fasterxml.jackson.databind.JsonNode data = campaignData.getData();
                        
                        // Check if campaign has a sent date
                        if (data.has("sentDate") && !data.get("sentDate").isNull()) {
                            String sentDateStr = data.get("sentDate").asText();
                            LocalDateTime sentDate = LocalDateTime.parse(sentDateStr);
                            return sentDate.isAfter(since);
                        }
                        
                        return false;
                    } catch (Exception e) {
                        logger.debug("Failed to parse campaign date: {}", e.getMessage());
                        return false;
                    }
                })
                .toList();
            
        } catch (Exception e) {
            logger.error("Failed to get recent campaigns since {}: {}", since, e.getMessage());
            return List.of();
        }
    }
}
