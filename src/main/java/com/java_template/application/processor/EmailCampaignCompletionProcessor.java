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

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Processor for email campaign completion workflow transition.
 * Handles the complete_sending transition (sending → sent).
 * 
 * Business Logic:
 * - Sets sentDate to current timestamp
 * - Calculates send duration
 * - Calculates delivery success rate
 * - Logs campaign completion statistics
 */
@Component
public class EmailCampaignCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignCompletionProcessor.class);
    private final ProcessorSerializer serializer;

    public EmailCampaignCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("EmailCampaignCompletionProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing email campaign completion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .validate(this::validateCompletionData, "Invalid completion data")
            .map(ctx -> {
                EmailCampaign campaign = ctx.entity();
                
                // Set final sent date if not already set
                if (campaign.getSentDate() == null) {
                    campaign.setSentDate(LocalDateTime.now());
                }
                
                // Calculate send duration
                Duration sendDuration = calculateSendDuration(campaign);
                
                // Calculate delivery success rate
                double successRate = calculateDeliverySuccessRate(campaign);
                
                // Log campaign completion statistics
                logger.info("Email campaign completed: {} - Duration: {}min, Success Rate: {:.2f}%, " +
                           "Successful: {}, Failed: {}, Total: {}", 
                           campaign.getCampaignName(),
                           sendDuration.toMinutes(),
                           successRate,
                           campaign.getSuccessfulDeliveries(),
                           campaign.getFailedDeliveries(),
                           campaign.getTotalSubscribers());
                
                return campaign;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignCompletionProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates completion data.
     */
    private boolean validateCompletionData(EmailCampaign campaign) {
        if (campaign == null) {
            logger.warn("Completion failed: EmailCampaign is null");
            return false;
        }
        
        // Campaign name must be set
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            logger.warn("Completion failed: Campaign name is required");
            return false;
        }
        
        // Delivery counters should be set
        if (campaign.getSuccessfulDeliveries() == null) {
            campaign.setSuccessfulDeliveries(0);
        }
        if (campaign.getFailedDeliveries() == null) {
            campaign.setFailedDeliveries(0);
        }
        
        // Total subscribers should be set
        if (campaign.getTotalSubscribers() == null) {
            logger.warn("Completion failed: Total subscribers not set");
            return false;
        }
        
        // Validate that delivery counts don't exceed total subscribers
        int totalDeliveries = campaign.getSuccessfulDeliveries() + campaign.getFailedDeliveries();
        if (totalDeliveries > campaign.getTotalSubscribers()) {
            logger.warn("Completion failed: Delivery count ({}) exceeds total subscribers ({})", 
                       totalDeliveries, campaign.getTotalSubscribers());
            return false;
        }
        
        logger.debug("Completion data validation passed");
        return true;
    }

    /**
     * Calculates the send duration.
     */
    private Duration calculateSendDuration(EmailCampaign campaign) {
        if (campaign.getScheduledDate() != null && campaign.getSentDate() != null) {
            return Duration.between(campaign.getScheduledDate(), campaign.getSentDate());
        } else if (campaign.getSentDate() != null) {
            // If no scheduled date, assume it started when sent date was set
            return Duration.ofMinutes(0);
        } else {
            return Duration.ofMinutes(0);
        }
    }

    /**
     * Calculates the delivery success rate.
     */
    private double calculateDeliverySuccessRate(EmailCampaign campaign) {
        int totalDeliveries = campaign.getSuccessfulDeliveries() + campaign.getFailedDeliveries();
        
        if (totalDeliveries == 0) {
            return 0.0;
        }
        
        return (campaign.getSuccessfulDeliveries() * 100.0) / totalDeliveries;
    }
}
