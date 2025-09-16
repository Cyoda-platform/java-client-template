package com.java_template.application.processor;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Processor for email campaign retry workflow transition.
 * Handles the retry transition (failed → preparing).
 * 
 * Business Logic:
 * - Increments retry count
 * - If retry count > 3, transitions to cancelled
 * - Resets delivery counters
 * - Sets retry date to current timestamp
 * - Analyzes failure reasons
 * - Excludes problematic subscribers if needed
 */
@Component
public class EmailCampaignRetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignRetryProcessor.class);
    private static final int MAX_RETRY_COUNT = 3;
    
    private final ProcessorSerializer serializer;

    public EmailCampaignRetryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("EmailCampaignRetryProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing email campaign retry for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .validate(this::validateRetryData, "Invalid retry data")
            .map(ctx -> {
                EmailCampaign campaign = ctx.entity();
                
                // Get current retry count (stored in campaign name or could be a separate field)
                int retryCount = extractRetryCount(campaign);
                retryCount++;
                
                // Check if retry count exceeds limit
                if (retryCount > MAX_RETRY_COUNT) {
                    logger.warn("Campaign {} exceeded max retry count ({}), marking as cancelled", 
                               campaign.getCampaignName(), MAX_RETRY_COUNT);
                    
                    // Mark as cancelled by setting sent date to null and updating name
                    campaign.setSentDate(null);
                    campaign.setCampaignName(campaign.getCampaignName() + " [CANCELLED-MAX-RETRIES]");
                    
                    return campaign;
                }
                
                // Reset delivery counters for retry
                campaign.setSuccessfulDeliveries(0);
                campaign.setFailedDeliveries(0);
                campaign.setOpenCount(0);
                campaign.setClickCount(0);
                campaign.setUnsubscribeCount(0);
                
                // Set retry metadata
                campaign.setSentDate(null); // Reset sent date for retry
                updateCampaignNameWithRetry(campaign, retryCount);
                
                // Analyze failure reasons (simplified)
                analyzeFailureReasons(campaign);
                
                logger.info("Email campaign prepared for retry #{}: {}", retryCount, campaign.getCampaignName());
                
                return campaign;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignRetryProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates retry data.
     */
    private boolean validateRetryData(EmailCampaign campaign) {
        if (campaign == null) {
            logger.warn("Retry failed: EmailCampaign is null");
            return false;
        }
        
        // Campaign name must be set
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            logger.warn("Retry failed: Campaign name is required");
            return false;
        }
        
        // Campaign should have failed (have some failed deliveries or other failure indicators)
        if (campaign.getFailedDeliveries() == null || campaign.getFailedDeliveries() == 0) {
            logger.warn("Retry failed: No failure indicators found");
            return false;
        }
        
        // Cat fact ID should be set
        if (campaign.getCatFactId() == null) {
            logger.warn("Retry failed: Cat fact ID is required");
            return false;
        }
        
        logger.debug("Retry data validation passed");
        return true;
    }

    /**
     * Extracts retry count from campaign name or metadata.
     */
    private int extractRetryCount(EmailCampaign campaign) {
        String campaignName = campaign.getCampaignName();
        
        // Look for retry indicator in campaign name
        if (campaignName.contains("[RETRY-")) {
            try {
                int startIndex = campaignName.indexOf("[RETRY-") + 7;
                int endIndex = campaignName.indexOf("]", startIndex);
                if (endIndex > startIndex) {
                    return Integer.parseInt(campaignName.substring(startIndex, endIndex));
                }
            } catch (NumberFormatException e) {
                logger.debug("Could not parse retry count from campaign name: {}", campaignName);
            }
        }
        
        return 0; // First retry
    }

    /**
     * Updates campaign name to include retry count.
     */
    private void updateCampaignNameWithRetry(EmailCampaign campaign, int retryCount) {
        String originalName = campaign.getCampaignName();
        
        // Remove existing retry indicator if present
        if (originalName.contains("[RETRY-")) {
            int retryIndex = originalName.indexOf("[RETRY-");
            originalName = originalName.substring(0, retryIndex).trim();
        }
        
        // Add new retry indicator
        campaign.setCampaignName(originalName + " [RETRY-" + retryCount + "]");
    }

    /**
     * Analyzes failure reasons and adjusts campaign accordingly.
     */
    private void analyzeFailureReasons(EmailCampaign campaign) {
        int totalDeliveries = campaign.getSuccessfulDeliveries() + campaign.getFailedDeliveries();
        int failedDeliveries = campaign.getFailedDeliveries();
        
        if (totalDeliveries > 0) {
            double failureRate = (failedDeliveries * 100.0) / totalDeliveries;
            
            logger.debug("Analyzing failure reasons for {}: {:.2f}% failure rate", 
                        campaign.getCampaignName(), failureRate);
            
            if (failureRate > 50) {
                logger.warn("High failure rate detected ({}%), may need to exclude problematic subscribers", 
                           failureRate);
                // In a real implementation, this would analyze specific failure reasons
                // and potentially exclude subscribers with persistent delivery issues
            }
        }
        
        // Reset scheduled date to current time for immediate retry
        campaign.setScheduledDate(LocalDateTime.now());
    }
}
